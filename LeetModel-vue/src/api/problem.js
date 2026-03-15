import request from "./request"

export function getProblemList(){
  return request({
    url:"/problems",
    method:"get"
  })
}