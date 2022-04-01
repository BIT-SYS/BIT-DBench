import * as markdown from "/scripts/markdown-wasm/markdown.es.js";
await markdown.ready;

const bodyMarkDown = document.getElementById("body-md");
const bodyHTML = document.getElementById("body-preview");

bodyMarkDown.addEventListener("input", () => parse());

function parse() {
  bodyHTML.innerHTML = markdown.parse(bodyMarkDown.value);
}