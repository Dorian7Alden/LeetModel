export function formatAdminTime(value) {
  return value ? String(value).replace("T", " ").slice(0, 16) : "—";
}

export function teamName(teamMap, teamId) {
  return teamMap?.[String(teamId)]?.name || "—";
}

export function problemLabel(problemMap, problemId) {
  const problem = problemMap?.[String(problemId)];
  if (!problem) return { code: "—", title: "—" };
  return {
    code: problem.code ?? problem.problemNumber ?? "—",
    title: problem.title || "—",
  };
}

export async function copyAdminText(value) {
  const text = String(value ?? "");
  if (navigator.clipboard?.writeText) {
    await navigator.clipboard.writeText(text);
    return;
  }
  const textarea = document.createElement("textarea");
  textarea.value = text;
  textarea.style.position = "fixed";
  textarea.style.opacity = "0";
  document.body.appendChild(textarea);
  textarea.select();
  document.execCommand("copy");
  textarea.remove();
}
