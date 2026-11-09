const dns = require("dns");
const fs = require("fs");
const http = require("http");
const net = require("net");
const path = require("path");

const PORT = 8787;
const ROOT = path.join(__dirname, "..", "web");

function readBody(request) {
  return new Promise((resolve, reject) => {
    const chunks = [];
    request.on("data", (chunk) => chunks.push(chunk));
    request.on("end", () => {
      const raw = Buffer.concat(chunks).toString("utf8");
      if (!raw) {
        resolve({});
        return;
      }
      try {
        resolve(JSON.parse(raw));
      } catch (error) {
        reject(new Error("The request was not valid JSON."));
      }
    });
    request.on("error", reject);
  });
}

function sendJson(response, status, payload) {
  const body = JSON.stringify(payload);
  response.writeHead(status, {
    "Content-Type": "application/json",
    "Access-Control-Allow-Origin": "*",
    "Access-Control-Allow-Methods": "GET, POST, OPTIONS",
    "Access-Control-Allow-Headers": "Content-Type"
  });
  response.end(body);
}

function resolveMx(domain) {
  return new Promise((resolve, reject) => {
    const resolver = new dns.Resolver();
    resolver.setServers(["8.8.8.8", "1.1.1.1"]);
    resolver.resolveMx(domain, (error, records) => {
      if (error || !records || !records.length) {
        reject(error || new Error("No mail server was found for " + domain));
        return;
      }
      records.sort((a, b) => a.priority - b.priority);
      resolve(records.map((record) => record.exchange));
    });
  });
}

function readReply(socket) {
  return new Promise((resolve, reject) => {
    let buffer = "";
    const timer = setTimeout(() => {
      cleanup();
      reject(new Error("The university mail server did not answer."));
    }, 20000);
    function cleanup() {
      clearTimeout(timer);
      socket.off("data", onData);
    }
    function onData(chunk) {
      buffer += chunk.toString("utf8");
      const lines = buffer.split(/\r?\n/).filter((line) => line.length > 0);
      const last = lines[lines.length - 1];
      if (last && /^\d{3} /.test(last)) {
        cleanup();
        resolve({ code: Number(last.slice(0, 3)), text: lines.join(" ") });
      }
    }
    socket.on("data", onData);
  });
}

function sendCommand(socket, command) {
  socket.write(command + "\r\n");
  return readReply(socket);
}

function deliverToHost(host, message) {
  return new Promise((resolve, reject) => {
    const socket = net.connect(25, host);
    const timer = setTimeout(() => {
      socket.destroy();
      reject(new Error("Could not reach " + host));
    }, 15000);
    socket.once("error", (error) => {
      clearTimeout(timer);
      reject(error);
    });
    socket.once("connect", async () => {
      clearTimeout(timer);
      try {
        const greeting = await readReply(socket);
        if (greeting.code !== 220) {
          throw new Error(greeting.text);
        }
        const hello = await sendCommand(socket, "EHLO queuely.local");
        if (hello.code !== 250) {
          throw new Error(hello.text);
        }
        const from = await sendCommand(socket, "MAIL FROM:<queuely@usiu.ac.ke>");
        if (from.code !== 250) {
          throw new Error(from.text);
        }
        const recipient = await sendCommand(socket, "RCPT TO:<" + message.to + ">");
        if (recipient.code !== 250 && recipient.code !== 251) {
          throw new Error(recipient.text);
        }
        const data = await sendCommand(socket, "DATA");
        if (data.code !== 354) {
          throw new Error(data.text);
        }
        const body = message.body.replace(/\r?\n/g, "\r\n").split("\r\n")
          .map((line) => (line.startsWith(".") ? "." + line : line))
          .join("\r\n");
        socket.write(
          "From: QueueLy <queuely@usiu.ac.ke>\r\n" +
          "To: " + message.to + "\r\n" +
          "Subject: " + message.subject.replace(/[\r\n]/g, " ") + "\r\n" +
          "Date: " + new Date().toUTCString() + "\r\n" +
          "\r\n" +
          body + "\r\n.\r\n"
        );
        const accepted = await readReply(socket);
        socket.write("QUIT\r\n");
        socket.end();
        if (accepted.code !== 250) {
          throw new Error(accepted.text);
        }
        resolve(accepted.text);
      } catch (error) {
        socket.destroy();
        reject(error);
      }
    });
  });
}

async function sendMail(message) {
  const domain = message.to.split("@")[1];
  const hosts = await resolveMx(domain);
  let lastError = null;
  for (const host of hosts) {
    try {
      await deliverToHost(host, message);
      return;
    } catch (error) {
      lastError = error;
    }
  }
  const failure = lastError || new Error("The university mail server refused the message.");
  failure.status = 502;
  throw failure;
}

function serveFile(response, filePath) {
  const types = {
    ".html": "text/html; charset=utf-8",
    ".css": "text/css; charset=utf-8",
    ".js": "text/javascript; charset=utf-8"
  };
  fs.readFile(filePath, (error, data) => {
    if (error) {
      response.writeHead(404, { "Content-Type": "text/plain" });
      response.end("Not found");
      return;
    }
    response.writeHead(200, { "Content-Type": types[path.extname(filePath)] || "application/octet-stream" });
    response.end(data);
  });
}

const server = http.createServer(async (request, response) => {
  if (request.method === "OPTIONS") {
    sendJson(response, 204, {});
    return;
  }

  const url = new URL(request.url, "http://127.0.0.1");

  if (url.pathname === "/api/mail" && request.method === "POST") {
    try {
      const body = await readBody(request);
      const to = String(body.to || "").trim().toLowerCase();
      const subject = String(body.subject || "").trim();
      const text = String(body.body || "");
      if (!/^[a-z]{3,30}@usiu\.ac\.ke$/.test(to)) {
        sendJson(response, 400, { error: "QueueLy only sends to a name-based USIU email, such as amchol@usiu.ac.ke." });
        return;
      }
      if (!subject || !text) {
        sendJson(response, 400, { error: "A subject and a message are required." });
        return;
      }
      await sendMail({ to, subject, body: text });
      sendJson(response, 200, { ok: true, to });
    } catch (error) {
      sendJson(response, error.status || 502, { error: error.message });
    }
    return;
  }

  const requested = url.pathname === "/" ? "/index.html" : url.pathname;
  const filePath = path.normalize(path.join(ROOT, requested));
  if (!filePath.startsWith(ROOT)) {
    response.writeHead(403);
    response.end("Forbidden");
    return;
  }
  serveFile(response, filePath);
});

server.listen(PORT, "0.0.0.0", () => {
  console.log("QueueLy mail server listening on http://127.0.0.1:" + PORT);
});
