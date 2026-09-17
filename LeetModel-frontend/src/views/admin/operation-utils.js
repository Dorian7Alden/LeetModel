import { ref } from "vue";
import { getAdminTeamReferences } from "@/api/admin-ops";
import { getPublicProblemList } from "@/api/problem";

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

const sharedTeamMap = ref({});
const sharedProblemMap = ref({});
const requestedTeamIds = new Set();
let problemPromise = null;

export function useAdminReferences() {
  async function loadProblems() {
    if (Object.keys(sharedProblemMap.value).length > 0) return sharedProblemMap.value;
    if (!problemPromise) {
      problemPromise = getPublicProblemList({ page: 1, pageSize: 200 })
        .then((res) => {
          sharedProblemMap.value = Object.fromEntries(
            (res.data?.rows || []).map((item) => [String(item.id), item])
          );
          return sharedProblemMap.value;
        })
        .catch(() => ({}))
        .finally(() => {
          problemPromise = null;
        });
    }
    return problemPromise;
  }

  async function loadTeams(teamIds) {
    const ids = [...new Set((teamIds || []).filter(Boolean).map(String))];
    const missing = ids.filter((id) => !sharedTeamMap.value[id] && !requestedTeamIds.has(id));
    if (!missing.length) return sharedTeamMap.value;
    missing.forEach((id) => requestedTeamIds.add(id));
    try {
      const res = await getAdminTeamReferences(missing);
      const additions = Object.fromEntries(
        (res.data || []).map((item) => [String(item.id), item])
      );
      sharedTeamMap.value = { ...sharedTeamMap.value, ...additions };
    } catch {
      // ignore
    }
    return sharedTeamMap.value;
  }

  return {
    teamMap: sharedTeamMap,
    problemMap: sharedProblemMap,
    loadProblems,
    loadTeams,
  };
}
