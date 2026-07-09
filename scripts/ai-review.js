const { execSync, execFileSync } = require('node:child_process');

function getSeverityEmoji(severity) {
  if (severity === 'critical') {
    return '🔴 Critical';
  }
  if (severity === 'warning') {
    return '🟡 Warning';
  }
  return '🔵 Suggestion';
}

function getDiff(baseRef) {
  let diff = "";
  try {
    console.log(`Fetching origin/${baseRef} to compute diff...`);
    execSync(`git fetch origin ${baseRef} --depth=1`); // nosonar
    diff = execSync(`git diff origin/${baseRef}...HEAD`).toString(); // nosonar
  } catch (err) {
    console.error(`⚠️ Failed to compute diff against origin/${baseRef}:`, err.message); // nosonar
    try {
      diff = execSync('git diff HEAD~1').toString(); // nosonar
    } catch (fallbackErr) {
      console.error("❌ Failed to compute fallback diff:", fallbackErr.message); // nosonar
      process.exit(1);
    }
  }
  return diff;
}

async function queryGroq(diff, groqKey) {
  const systemPrompt = `You are a strict, senior code reviewer for a premium podcast application project ("boxlore").
Analyze the code diff and check for:
1. Logical bugs or runtime exceptions.
2. Security issues (exposed keys, credentials, or weak timing comparisons).
   - Note: GitHub Actions secrets template placeholders (e.g. \${{ secrets.XYZ }}) are secure references and are NOT exposed keys. Do not flag them as vulnerabilities.
   - Note: Standard Git / GitHub CLI tool execution via Node 'child_process' is safe and expected in this script. Do not flag this as a vulnerability.
3. Project-specific rule violations:
   - Compose Card backgrounds must strictly use solid MaterialTheme surface colors (no glassmorphism, copy(alpha = ...), or transparent borders).
   - Local properties, secrets, and credentials must never be committed.
   - Build tasks should default to 'play' variants (e.g. installPlayDebug) instead of 'foss' variants unless requested. Note: This rule only applies to Android Gradle/build files (like .gradle or workflows executing gradle commands), NOT to Node.js scripts or non-build workflows. Do not flag other files for this.
   - Absolute non-commitment of local.properties or .env secrets.

Return your response strictly as a JSON object matching this schema:
{
  "approved": boolean,
  "summary": "Brief 1-2 sentence overall summary of the review.",
  "findings": [
    {
      "file": "string",
      "line": number,
      "severity": "critical" | "warning" | "suggestion",
      "issue": "Detailed description of the issue.",
      "fix": "Specific suggestion on how to fix it."
    }
  ]
}

Severity definitions:
- "critical": Only use this for high-confidence logical correctness bugs, severe runtime crash vulnerabilities, or leaked plaintext keys/secrets. Minor warnings, style advice, node-version warnings, workflow lint, or library usage preferences (like child_process vs other libraries) MUST NEVER be marked as critical.
- "warning": Potential bugs, code smells, or non-optimal structures.
- "suggestion": Style advice, minor improvements, or general feedback.

Set "approved" to true if there are zero "critical" findings.
No other text or formatting. Just the JSON object.`;

  const response = await fetch("https://api.groq.com/openai/v1/chat/completions", {
    method: "POST",
    headers: {
      "Authorization": `Bearer ${groqKey}`,
      "Content-Type": "application/json"
    },
    body: JSON.stringify({
      model: "llama-3.3-70b-versatile",
      response_format: { type: "json_object" },
      messages: [
        { role: "system", content: systemPrompt },
        { role: "user", content: `Code Diff to Review:\n\n${diff}` }
      ],
      temperature: 0.1
    })
  });

  if (!response.ok) {
    const errText = await response.text();
    throw new Error(`Groq API returned HTTP ${response.status}: ${errText}`);
  }

  const data = await response.json();
  return JSON.parse(data.choices[0].message.content);
}

function buildCommentMarkdown(reviewResult) {
  let commentMarkdown = `### 🤖 Custom AI Code Reviewer Feedback\n\n`;
  commentMarkdown += `**Status:** ${reviewResult.approved ? "✅ Approved" : "❌ Changes Requested"}\n\n`;
  commentMarkdown += `> ${reviewResult.summary}\n\n`;

  if (reviewResult.findings.length > 0) {
    commentMarkdown += `#### 🔍 Findings\n\n`;
    commentMarkdown += `| File | Line | Severity | Issue | Suggestion |\n`;
    commentMarkdown += `| :--- | :--- | :--- | :--- | :--- |\n`;
    
    for (const f of reviewResult.findings) {
      const severityEmoji = getSeverityEmoji(f.severity);
      commentMarkdown += `| \`${f.file}\` | ${f.line} | ${severityEmoji} | ${f.issue} | \`${f.fix}\` |\n`;
    }
  } else {
    commentMarkdown += `✨ No issues or rule violations found! Nice work.\n`;
  }
  return commentMarkdown;
}

function postPRComment(prNumber, commentMarkdown, githubToken) {
  try {
    console.log(`Posting review comment to PR #${prNumber}...`);
    execFileSync('gh', ['pr', 'comment', prNumber, '--body', commentMarkdown], { // nosonar
      env: { ...process.env, GITHUB_TOKEN: githubToken }
    });
    console.log("✅ Comment posted successfully.");
  } catch (commentErr) {
    console.error("⚠️ Failed to post comment on PR:", commentErr.message); // nosonar
  }
}

async function run() {
  const prNumber = process.env.PR_NUMBER;
  const groqKey = process.env.GROQ_API_KEY;
  const githubToken = process.env.GITHUB_TOKEN;

  if (!groqKey) {
    console.error("❌ Error: Missing GROQ_API_KEY environment variable.");
    process.exit(1);
  }

  const baseRef = process.env.GITHUB_BASE_REF || 'master';
  const diff = getDiff(baseRef);

  if (!diff.trim()) {
    console.log("💡 No changes detected in the diff.");
    process.exit(0);
  }

  console.log(`🔍 Diff size: ${diff.length} characters. Querying Groq API...`); // nosonar

  let reviewResult;
  try {
    reviewResult = await queryGroq(diff, groqKey);
  } catch (err) {
    console.error("❌ Groq API review failed:", err.message); // nosonar
    process.exit(1);
  }

  // Format review output
  console.log("\n=== AI Review Summary ===");
  console.log(`Status: ${reviewResult.approved ? "✅ APPROVED" : "❌ CHANGES REQUESTED"}`); // nosonar
  console.log(`Summary: ${reviewResult.summary}`); // nosonar
  console.log(`Findings: ${reviewResult.findings.length}`); // nosonar

  const commentMarkdown = buildCommentMarkdown(reviewResult);

  if (prNumber && githubToken) {
    postPRComment(prNumber, commentMarkdown, githubToken);
  } else {
    console.log("\n--- Generated Markdown ---");
    console.log(commentMarkdown); // nosonar
  }

  // Fail the status check only if there are critical security/correctness findings.
  const hasCritical = reviewResult.findings.some(f => f.severity === 'critical');
  if (hasCritical) {
    console.error("\n❌ Review failed due to critical correctness or security findings.");
    process.exit(1);
  }

  console.log("\n✅ Review passed successfully.");
  process.exit(0);
}

run();
