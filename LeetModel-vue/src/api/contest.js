import request from "./request";

export function getLatestContest() {
  return request({
    url: "/competitions/competition/latest3",
    method: "get",
  });
}
export function getContestDetail(id) {
  return request({
    url: `/contest/${id}`,
    method: "get",
  });
}
