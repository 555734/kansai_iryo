(() => {
  if (window.__TRIAI_UNIFIED_EXTRACTOR__) return;
  window.__TRIAI_UNIFIED_EXTRACTOR__ = true;

  const host = location.hostname;
  const provider = host.includes("chatgpt.com")
    ? "ChatGPT"
    : host.includes("gemini.google.com")
      ? "Gemini"
      : host.includes("claude.ai")
        ? "Claude"
        : "Unknown";

  if (provider === "Unknown") return;

  let timer = null;
  let lastPayload = "";

  const clean = (value) => String(value || "")
    .replace(/\u00a0/g, " ")
    .replace(/[ \t]+\n/g, "\n")
    .replace(/\n{3,}/g, "\n\n")
    .trim();

  const nodeText = (node) => {
    if (!node) return "";
    return clean(node.innerText || node.textContent || "").slice(0, 30000);
  };

  const firstNonEmptySelector = (selectors) => {
    for (const selector of selectors) {
      const nodes = [...document.querySelectorAll(selector)];
      if (nodes.length) return nodes;
    }
    return [];
  };

  const sortInDocumentOrder = (items) => items.sort((a, b) => {
    if (a.node === b.node) return 0;
    const relation = a.node.compareDocumentPosition(b.node);
    return relation & Node.DOCUMENT_POSITION_PRECEDING ? 1 : -1;
  });

  function extractChatGPT() {
    const roleNodes = [...document.querySelectorAll(
      '[data-message-author-role="user"], [data-message-author-role="assistant"]'
    )];

    return roleNodes.map((node) => ({
      role: node.getAttribute("data-message-author-role"),
      text: nodeText(node)
    })).filter((item) => item.text && (item.role === "user" || item.role === "assistant"));
  }

  function extractClaude() {
    const users = firstNonEmptySelector([
      '[data-testid="user-message"]',
      '[data-testid*="user-message"]'
    ]).map((node) => ({ role: "user", node }));

    const assistants = firstNonEmptySelector([
      '.font-claude-response',
      '[data-is-streaming] .font-claude-response',
      '[data-testid*="assistant"] .font-claude-response'
    ]).map((node) => ({ role: "assistant", node }));

    return sortInDocumentOrder([...users, ...assistants])
      .map((item) => ({ role: item.role, text: nodeText(item.node) }))
      .filter((item) => item.text);
  }

  function extractGemini() {
    const users = firstNonEmptySelector([
      'user-query',
      '.query-text',
      '.user-query',
      '[data-message-author="user"]'
    ]).map((node) => ({ role: "user", node }));

    const assistants = firstNonEmptySelector([
      'model-response',
      'message-content',
      '.model-response-text',
      '.response-content'
    ]).map((node) => ({ role: "assistant", node }));

    return sortInDocumentOrder([...users, ...assistants])
      .map((item) => ({ role: item.role, text: nodeText(item.node) }))
      .filter((item) => item.text);
  }

  function extractMessages() {
    if (provider === "ChatGPT") return extractChatGPT();
    if (provider === "Gemini") return extractGemini();
    return extractClaude();
  }

  function conversationTitle() {
    if (provider === "Claude") {
      const header = document.querySelector('[data-testid="page-header"]');
      const text = nodeText(header).split("\n")[0];
      if (text) return text;
    }
    const title = clean(document.title);
    return title || provider;
  }

  function historyCandidates() {
    if (provider === "ChatGPT") {
      return [...document.querySelectorAll('a[href^="/c/"], a[href*="chatgpt.com/c/"]')];
    }
    if (provider === "Gemini") {
      return [...document.querySelectorAll('a[href^="/app/"], a[href*="gemini.google.com/app/"]')]
        .filter((anchor) => /\/app\/[^/?#]+/.test(anchor.href || ""));
    }
    return [...document.querySelectorAll('a[href^="/chat/"], a[href*="/chat/"]')];
  }

  function extractHistory() {
    const seen = new Set();
    const result = [];

    for (const anchor of historyCandidates()) {
      const rawHref = anchor.getAttribute("href") || anchor.href || "";
      if (!rawHref) continue;

      let url;
      try {
        url = new URL(rawHref, location.origin).href;
      } catch (_) {
        continue;
      }

      if (seen.has(url)) continue;
      const title = nodeText(anchor).split("\n")[0].trim();
      if (!title || title.length > 300) continue;

      seen.add(url);
      result.push({ title, url });
      if (result.length >= 120) break;
    }

    return result;
  }

  function sendSnapshot() {
    timer = null;
    const messages = extractMessages().slice(-80);
    const payload = {
      type: "conversationSnapshot",
      provider,
      title: conversationTitle(),
      url: location.href,
      messages,
      history: extractHistory()
    };
    const serialized = JSON.stringify(payload);
    if (serialized === lastPayload) return;
    lastPayload = serialized;
    browser.runtime.sendNativeMessage("triai", payload).catch(() => {});
  }

  function schedule() {
    clearTimeout(timer);
    timer = setTimeout(sendSnapshot, 650);
  }

  function start() {
    schedule();
    if (!document.documentElement) return;
    const observer = new MutationObserver(schedule);
    observer.observe(document.documentElement, {
      subtree: true,
      childList: true,
      characterData: true
    });
    window.addEventListener("popstate", schedule);
    window.addEventListener("hashchange", schedule);
    document.addEventListener("visibilitychange", () => {
      if (!document.hidden) schedule();
    });
    setInterval(schedule, 5000);
  }

  start();
})();
