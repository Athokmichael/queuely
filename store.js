const STORAGE_KEY = "queuely-data-v2";
const SESSION_KEY = "queuely-session";

const SCHOOLS = [
  { id: "business", name: "Chandaria School of Business", short: "Business" },
  { id: "sst", name: "School of Science and Technology", short: "Science and Technology" },
  { id: "chss", name: "School of Humanities and Social Sciences", short: "Humanities and Social Sciences" },
  { id: "scca", name: "School of Communication, Cinematic and Creative Arts", short: "Communication and Creative Arts" },
  { id: "sphs", name: "School of Pharmacy and Health Sciences", short: "Pharmacy and Health Sciences" }
];

const CATALOG = {
  business: [
    { code: "BUS 1010", title: "Introduction to Business", time: "Mon 09:00" },
    { code: "ACC 2010", title: "Financial Accounting", time: "Tue 11:00" },
    { code: "MKT 3010", title: "Marketing Management", time: "Wed 14:00" },
    { code: "HTM 2200", title: "Hospitality Operations", time: "Thu 10:00" }
  ],
  sst: [
    { code: "SWE 3090", title: "Software Engineering", time: "Mon 10:00" },
    { code: "MTH 2210", title: "Calculus II", time: "Tue 14:00" },
    { code: "COM 1500", title: "Communication Skills", time: "Wed 09:00" },
    { code: "IST 2040", title: "Database Systems", time: "Thu 11:00" },
    { code: "SWE 4020", title: "Software Project", time: "Mon 10:00" }
  ],
  chss: [
    { code: "PSY 1100", title: "Introduction to Psychology", time: "Mon 08:00" },
    { code: "SOC 2200", title: "Social Theory", time: "Tue 13:00" },
    { code: "IRL 3100", title: "International Relations", time: "Thu 15:00" }
  ],
  scca: [
    { code: "JRN 2100", title: "News Writing", time: "Mon 11:00" },
    { code: "FLM 2400", title: "Film Production", time: "Wed 16:00" },
    { code: "CMM 1800", title: "Media Studies", time: "Fri 09:00" }
  ],
  sphs: [
    { code: "PHA 2100", title: "Pharmaceutical Chemistry", time: "Mon 08:00" },
    { code: "NUR 2300", title: "Foundations of Nursing", time: "Tue 10:00" },
    { code: "BIO 2500", title: "Human Physiology", time: "Thu 14:00" }
  ]
};

const REASONS = [
  { id: "add", label: "Add Course" },
  { id: "drop", label: "Drop Course" },
  { id: "consult", label: "Academic Consultation" }
];

function lettersOnly(value) {
  return String(value || "").normalize("NFD").replace(/[^a-z]/gi, "").toLowerCase();
}

function titleName(value) {
  const clean = String(value || "").trim().replace(/\s+/g, " ");
  return clean.replace(/\b([a-z])/gi, (letter) => letter.toUpperCase());
}

function usiuEmail(givenName, surname, taken) {
  const given = lettersOnly(givenName);
  const family = lettersOnly(surname);
  if (given.length < 1 || family.length < 2) {
    return { error: "Enter a given name and a surname. QueueLy builds the USIU email from them, for example Ann Mchol becomes amchol@usiu.ac.ke." };
  }
  for (let i = 1; i <= given.length; i++) {
    const local = given.slice(0, i) + family;
    if (local.length < 3 || local.length > 30) {
      continue;
    }
    const email = local + "@usiu.ac.ke";
    if (!taken(email)) {
      return { email, name: titleName(givenName) + " " + titleName(surname) };
    }
  }
  return { error: "That name is already used. USIU emails look like jsmith@usiu.ac.ke." };
}

function schoolById(id) {
  return SCHOOLS.find((school) => school.id === id);
}

function reasonLabel(id) {
  const reason = REASONS.find((item) => item.id === id);
  return reason ? reason.label : id;
}

function blankData() {
  return {
    nextTicket: 44,
    users: [
      {
        email: "admin@usiu.ac.ke",
        password: "Queuely123",
        role: "admin",
        mustChangePassword: false,
        name: "QueueLy Admin"
      },
      {
        email: "advisor@usiu.ac.ke",
        password: "Queuely123",
        role: "advisor",
        mustChangePassword: false,
        name: "Course Advisor"
      },
      {
        email: "amchol@usiu.ac.ke",
        password: "Queuely123",
        role: "student",
        mustChangePassword: false,
        schoolId: "670375",
        name: "Student 670375",
        program: "BSc Software Engineering"
      },
      {
        email: "jsmith@usiu.ac.ke",
        password: "Queuely123",
        role: "student",
        mustChangePassword: false,
        schoolId: "671440",
        name: "Student 671440",
        program: "BSc Information Systems"
      }
    ],
    sheets: {
      "670375": [
        { code: "SWE 3090", title: "Software Engineering", time: "Mon 10:00" },
        { code: "MTH 2210", title: "Calculus II", time: "Tue 14:00" },
        { code: "COM 1500", title: "Communication Skills", time: "Wed 09:00" }
      ],
      "671440": [
        { code: "IST 2040", title: "Database Systems", time: "Thu 11:00" },
        { code: "COM 1500", title: "Communication Skills", time: "Wed 09:00" }
      ]
    },
    queue: [
      {
        ticket: "A-042",
        school: "sst",
        email: "amchol@usiu.ac.ke",
        schoolId: "670375",
        reason: "drop",
        status: "waiting",
        joinedAt: "2026-11-09T08:10:00.000Z",
        mailed: { joined: true, next: false, turn: false, closed: false },
        changes: []
      },
      {
        ticket: "A-043",
        school: "sst",
        email: "jsmith@usiu.ac.ke",
        schoolId: "671440",
        reason: "add",
        status: "waiting",
        joinedAt: "2026-11-09T08:24:00.000Z",
        mailed: { joined: true, next: false, turn: false, closed: false },
        changes: []
      }
    ],
    outbox: [],
    history: []
  };
}

function load() {
  const raw = localStorage.getItem(STORAGE_KEY);
  if (!raw) {
    const seeded = blankData();
    localStorage.setItem(STORAGE_KEY, JSON.stringify(seeded));
    return seeded;
  }
  return JSON.parse(raw);
}

function save(data) {
  localStorage.setItem(STORAGE_KEY, JSON.stringify(data));
}

function liveLine(data, school) {
  return data.queue
    .filter((entry) => entry.school === school && entry.status !== "closed")
    .sort((a, b) => a.joinedAt.localeCompare(b.joinedAt));
}

function userByEmail(data, email) {
  return data.users.find((user) => user.email === email.toLowerCase());
}

function activeVisit(data, email) {
  return data.queue.find((entry) => entry.email === email && entry.status !== "closed");
}

function pushMail(data, to, subject, body) {
  const mail = {
    id: Date.now().toString(36) + Math.random().toString(36).slice(2, 8),
    to,
    subject,
    body,
    at: new Date().toISOString(),
    status: "sending",
    detail: ""
  };
  data.outbox.unshift(mail);
  deliver(mail);
}

function deliver(mail) {
  if (typeof fetch !== "function") {
    return;
  }
  const base = typeof location !== "undefined" && location.protocol === "file:"
    ? "http://127.0.0.1:8787"
    : "";
  setTimeout(() => {
    fetch(base + "/api/mail", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ to: mail.to, subject: mail.subject, body: mail.body })
    }).then(async (response) => {
      const payload = await response.json().catch(() => ({}));
      markMail(mail.id, response.ok ? "sent" : "failed", payload.error || "");
    }).catch(() => {
      markMail(mail.id, "failed", "The mail server is not running.");
    });
  }, 40);
}

let mailWrite = Promise.resolve();

function markMail(id, status, detail) {
  mailWrite = mailWrite.then(() => {
    const data = load();
    const mail = data.outbox.find((item) => item.id === id);
    if (!mail) {
      return;
    }
    mail.status = status;
    mail.detail = detail;
    save(data);
    if (typeof window !== "undefined") {
      window.dispatchEvent(new CustomEvent("queuely-mail", { detail: mail }));
    }
  });
}

function syncNextMails(data, school) {
  const line = liveLine(data, school);
  const servingAt = line.findIndex((entry) => entry.status === "serving");
  if (servingAt < 0) {
    return;
  }
  const follower = line[servingAt + 1];
  if (follower && !follower.mailed.next) {
    follower.mailed.next = true;
    const user = userByEmail(data, follower.email);
    pushMail(
      data,
      follower.email,
      "You are next in the QueueLy line",
      user.name + " (" + user.schoolId + "), you are next at " + schoolById(school).name + ". Only the student being served is ahead of you. Ticket " + follower.ticket + "."
    );
  }
}

function sheetCopy(data, schoolId) {
  const rows = data.sheets[schoolId] || [];
  return rows.map((row) => Object.assign({}, row));
}

const Store = {
  schools: SCHOOLS,
  reasons: REASONS,
  catalog: CATALOG,
  schoolById,
  reasonLabel,

  session() {
    const raw = sessionStorage.getItem(SESSION_KEY);
    return raw ? JSON.parse(raw) : null;
  },

  setSession(email) {
    sessionStorage.setItem(SESSION_KEY, JSON.stringify({ email }));
  },

  logout() {
    sessionStorage.removeItem(SESSION_KEY);
  },

  currentUser() {
    const session = this.session();
    if (!session) {
      return null;
    }
    return userByEmail(load(), session.email) || null;
  },

  login(email, password) {
    const normalized = email.trim().toLowerCase();
    if (!normalized.endsWith("@usiu.ac.ke")) {
      return { ok: false, error: "Use your USIU email, ending in @usiu.ac.ke." };
    }
    const user = userByEmail(load(), normalized);
    if (!user || user.password !== password) {
      return { ok: false, error: "That email or password does not match a QueueLy account. Accounts are created by the system admin." };
    }
    this.setSession(user.email);
    return { ok: true, user };
  },

  changePassword(currentPassword, nextPassword) {
    const data = load();
    const session = this.session();
    const user = userByEmail(data, session.email);
    if (!user || user.password !== currentPassword) {
      return { ok: false, error: "The current password is not correct." };
    }
    if (nextPassword.length < 6) {
      return { ok: false, error: "Choose a password of at least 6 characters." };
    }
    if (nextPassword === currentPassword) {
      return { ok: false, error: "Choose a password that is different from the one you were given." };
    }
    user.password = nextPassword;
    user.mustChangePassword = false;
    save(data);
    return { ok: true };
  },

  people() {
    return load().users.map((user) => ({
      email: user.email,
      role: user.role,
      schoolId: user.schoolId || "",
      name: user.name,
      mustChangePassword: user.mustChangePassword
    }));
  },

  outbox() {
    return load().outbox.slice();
  },

  myMail() {
    const user = this.currentUser();
    if (!user) {
      return [];
    }
    return load().outbox.filter((mail) => mail.to === user.email);
  },

  addPerson(givenName, surname, role, schoolId) {
    const data = load();
    const built = usiuEmail(givenName, surname, (email) => userByEmail(data, email));
    if (built.error) {
      return { ok: false, error: built.error };
    }
    const normalized = built.email;
    if (role !== "student" && role !== "advisor") {
      return { ok: false, error: "Choose student or advisor." };
    }
    let id = "";
    if (role === "student") {
      id = schoolId.trim();
      if (!/^\d{5,8}$/.test(id)) {
        return { ok: false, error: "Enter the school ID the student already has, such as 670375." };
      }
      if (data.users.some((user) => user.schoolId === id)) {
        return { ok: false, error: "That school ID is already on another account." };
      }
    }
    const temp = "Ql-" + Math.floor(1000 + Math.random() * 9000);
    const user = {
      email: normalized,
      password: temp,
      role,
      mustChangePassword: true,
      name: built.name
    };
    if (role === "student") {
      user.schoolId = id;
      user.program = "Program on the school datasheet";
      data.sheets[id] = [
        { code: "COM 1500", title: "Communication Skills", time: "Wed 09:00" }
      ];
    }
    data.users.push(user);
    pushMail(
      data,
      normalized,
      "Your QueueLy password",
      "A QueueLy account was created for " + normalized + ". Your first password is " + temp + ". Sign in with your USIU email and change this password before you continue. QueueLy does not create a new student ID."
    );
    save(data);
    return { ok: true, email: normalized, temp };
  },

  resetPassword(email) {
    const data = load();
    const user = userByEmail(data, email);
    if (!user || user.role === "admin") {
      return { ok: false, error: "That account cannot be reset here." };
    }
    const temp = "Ql-" + Math.floor(1000 + Math.random() * 9000);
    user.password = temp;
    user.mustChangePassword = true;
    pushMail(
      data,
      user.email,
      "Your new QueueLy password",
      "The system admin reset the QueueLy password for " + user.email + ". Your temporary password is " + temp + ". Change it the next time you sign in."
    );
    save(data);
    return { ok: true, temp };
  },

  line(school) {
    return liveLine(load(), school);
  },

  front(school) {
    return liveLine(load(), school)[0] || null;
  },

  datasheet(schoolId) {
    const data = load();
    const user = data.users.find((item) => item.schoolId === schoolId);
    if (!user) {
      return null;
    }
    return {
      name: user.name,
      schoolId: user.schoolId,
      program: user.program,
      email: user.email,
      courses: sheetCopy(data, schoolId)
    };
  },

  openVisit(school, enteredId) {
    const data = load();
    const advisor = this.currentUser();
    const line = liveLine(data, school);
    const front = line[0];
    if (!front) {
      return { ok: false, error: "No one is waiting in this school queue." };
    }
    if (front.schoolId !== enteredId.trim()) {
      return { ok: false, error: "That school ID is not the student at the front of this queue." };
    }
    if (front.status !== "serving") {
      front.status = "serving";
      if (!front.mailed.turn) {
        front.mailed.turn = true;
        pushMail(
          data,
          front.email,
          "It is your turn with the course advisor",
          "Student " + front.schoolId + ", the advisor has opened your datasheet at " + schoolById(school).name + ". Ticket " + front.ticket + "."
        );
      }
      syncNextMails(data, school);
    }
    save(data);
    return { ok: true, entry: front, advisor: advisor.email };
  },

  addCourse(school, code) {
    const data = load();
    const front = liveLine(data, school)[0];
    if (!front || front.status !== "serving") {
      return { ok: false, error: "Open the student at the front before adding a course." };
    }
    const course = (CATALOG[school] || []).find((item) => item.code === code);
    if (!course) {
      return { ok: false, error: "That course is not offered in this school." };
    }
    const sheet = data.sheets[front.schoolId] || [];
    const active = sheet.filter((row) => row.mark !== "Drop approved by advisor");
    if (active.some((row) => row.code === course.code)) {
      return { ok: false, error: course.code + " is already on this datasheet." };
    }
    if (active.some((row) => row.time === course.time)) {
      return { ok: false, error: course.code + " meets at the same time as another registered course." };
    }
    const row = {
      code: course.code,
      title: course.title,
      time: course.time,
      mark: "Add approved by advisor",
      registrar: "Waiting for the registrar"
    };
    sheet.push(row);
    data.sheets[front.schoolId] = sheet;
    front.changes.push({ action: "add", code: course.code, title: course.title });
    save(data);
    return { ok: true };
  },

  dropCourse(school, code) {
    const data = load();
    const front = liveLine(data, school)[0];
    if (!front || front.status !== "serving") {
      return { ok: false, error: "Open the student at the front before dropping a course." };
    }
    const sheet = data.sheets[front.schoolId] || [];
    const row = sheet.find((item) => item.code === code && item.mark !== "Drop approved by advisor");
    if (!row) {
      return { ok: false, error: "That course is not an active registration on this datasheet." };
    }
    row.mark = "Drop approved by advisor";
    row.registrar = "Waiting for the registrar";
    front.changes.push({ action: "drop", code: row.code, title: row.title });
    save(data);
    return { ok: true };
  },

  closeVisit(school) {
    const data = load();
    const advisor = this.currentUser();
    const line = liveLine(data, school);
    const front = line[0];
    if (!front || front.status !== "serving") {
      return { ok: false, error: "Open the student at the front before you close the queue." };
    }
    front.status = "closed";
    front.closedAt = new Date().toISOString();
    front.advisor = advisor.email;
    front.mailed.closed = true;
    const changeText = front.changes.length
      ? front.changes.map((change) => (change.action === "add" ? "Add" : "Drop") + " of " + change.code).join("; ")
      : "No course change";
    pushMail(
      data,
      front.email,
      "Your advisor visit is closed",
      changeText + " recorded and your visit is closed. The record is waiting for the registrar. Ticket " + front.ticket + "."
    );
    data.history.unshift({
      ticket: front.ticket,
      email: front.email,
      schoolId: front.schoolId,
      school: front.school,
      reason: front.reason,
      changes: front.changes.slice(),
      advisor: advisor.email,
      closedAt: front.closedAt
    });
    syncNextMails(data, school);
    save(data);
    const next = liveLine(data, school)[0] || null;
    return { ok: true, closed: front, next, changeText };
  },

  joinQueue(school, reason) {
    const data = load();
    const user = this.currentUser();
    if (user.role !== "student") {
      return { ok: false, error: "Only a student can join a school queue." };
    }
    if (activeVisit(data, user.email)) {
      return { ok: false, error: "You already have an open visit. Leave that queue before joining another." };
    }
    const ticket = "A-" + String(data.nextTicket).padStart(3, "0");
    data.nextTicket += 1;
    const entry = {
      ticket,
      school,
      email: user.email,
      schoolId: user.schoolId,
      reason,
      status: "waiting",
      joinedAt: new Date().toISOString(),
      mailed: { joined: true, next: false, turn: false, closed: false },
      changes: []
    };
    data.queue.push(entry);
    pushMail(
      data,
      user.email,
      "You joined the QueueLy line",
      user.name + ", you joined " + schoolById(school).name + " for " + reasonLabel(reason) + ". Your ticket is " + ticket + "."
    );
    syncNextMails(data, school);
    save(data);
    return { ok: true, entry };
  },

  leaveQueue() {
    const data = load();
    const user = this.currentUser();
    const entry = activeVisit(data, user.email);
    if (!entry) {
      return { ok: false, error: "You are not in a queue." };
    }
    if (entry.status === "serving") {
      return { ok: false, error: "The advisor has opened your datasheet, so this visit has to be closed by the advisor." };
    }
    const school = entry.school;
    entry.status = "closed";
    entry.left = true;
    save(data);
    return { ok: true, school };
  },

  myVisit() {
    const user = this.currentUser();
    if (!user) {
      return null;
    }
    const data = load();
    const entry = activeVisit(data, user.email);
    if (!entry) {
      return null;
    }
    const line = liveLine(data, entry.school);
    const index = line.findIndex((item) => item.ticket === entry.ticket);
    return {
      entry,
      ahead: index,
      wait: Math.max(index, entry.status === "serving" ? 0 : 1) * 6
    };
  },

  myHistory() {
    const user = this.currentUser();
    return load().history.filter((item) => item.email === user.email);
  }
};
