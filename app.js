const app = document.querySelector("#app");
let notice = "";
let selectedSchool = "";
let selectedReason = "";

function escapeHtml(value) {
  return String(value)
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;");
}

function formatWhen(iso) {
  return new Date(iso).toLocaleString([], { month: "short", day: "numeric", hour: "numeric", minute: "2-digit" });
}

function go() {
  const user = Store.currentUser();
  if (!user) {
    renderLogin();
    return;
  }
  if (user.mustChangePassword) {
    renderChangePassword();
    return;
  }
  if (user.role === "admin") {
    renderAdmin();
    return;
  }
  if (user.role === "advisor") {
    if (!selectedSchool) {
      renderAdvisorSchools();
      return;
    }
    renderAdvisorQueue();
    return;
  }
  const visit = Store.myVisit();
  if (visit) {
    renderStudentStatus();
    return;
  }
  if (!selectedSchool) {
    renderStudentSchool();
    return;
  }
  renderStudentReason();
}

function topbar(user) {
  return (
    '<header class="topbar"><div class="brand"><div class="mark">Q</div><div><b>QueueLy</b><span>USIU-AFRICA</span></div></div>' +
    '<div class="top-actions"><span>' + escapeHtml(user.email) + '</span>' +
    '<button class="btn btn-outline btn-small" id="logout">Log out</button></div></header>'
  );
}

function bindLogout() {
  const button = document.querySelector("#logout");
  if (button) {
    button.addEventListener("click", () => {
      selectedSchool = "";
      selectedReason = "";
      notice = "";
      Store.logout();
      go();
    });
  }
}

function renderLogin() {
  app.innerHTML =
    '<div class="hero-login"><div><div class="login-intro"><div class="mark">Q</div><h1>QueueLy</h1><p>USIU-Africa course advisor queue</p></div>' +
    '<form class="login-card" id="login-form"><h2>Sign in</h2><p class="muted">Use the USIU email on your QueueLy account. There is no create-account button.</p>' +
    '<label for="email">USIU email</label><input id="email" type="email" autocomplete="username" placeholder="amchol@usiu.ac.ke" required>' +
    '<label for="password">Password</label><input id="password" type="password" autocomplete="current-password" required>' +
    '<div id="form-error"></div><button class="btn btn-gold btn-block" type="submit">Log in</button></form>' +
    '<p class="demo">Demo password <code>Queuely123</code><br>admin@usiu.ac.ke · advisor@usiu.ac.ke · amchol@usiu.ac.ke</p></div></div>';

  document.querySelector("#login-form").addEventListener("submit", (event) => {
    event.preventDefault();
    const result = Store.login(
      document.querySelector("#email").value,
      document.querySelector("#password").value
    );
    if (!result.ok) {
      document.querySelector("#form-error").innerHTML = '<div class="error">' + escapeHtml(result.error) + "</div>";
      return;
    }
    selectedSchool = "";
    notice = "";
    go();
  });
}

function renderChangePassword() {
  const user = Store.currentUser();
  app.innerHTML =
    '<div class="hero-login"><form class="login-card" id="password-form"><h2>Change your password</h2>' +
    '<p class="muted">This is the first password sent to ' + escapeHtml(user.email) + '. Set a new one before you continue.</p>' +
    '<label for="current">Current password</label><input id="current" type="password" required>' +
    '<label for="next">New password</label><input id="next" type="password" required>' +
    '<div id="form-error"></div><button class="btn btn-gold btn-block" type="submit">Save password</button>' +
    '<button class="btn btn-outline-navy btn-block" id="logout" type="button">Log out</button></form></div>';

  document.querySelector("#password-form").addEventListener("submit", (event) => {
    event.preventDefault();
    const result = Store.changePassword(
      document.querySelector("#current").value,
      document.querySelector("#next").value
    );
    if (!result.ok) {
      document.querySelector("#form-error").innerHTML = '<div class="error">' + escapeHtml(result.error) + "</div>";
      return;
    }
    go();
  });
  bindLogout();
}

function mailStatus(mail) {
  if (mail.status === "sent") {
    return "Sent to " + mail.to;
  }
  if (mail.status === "failed") {
    return "Not sent to " + mail.to + (mail.detail ? ". " + mail.detail : "");
  }
  return "Sending to " + mail.to;
}

function mailList(mails) {
  if (!mails.length) {
    return '<p class="muted">No email yet.</p>';
  }
  return mails.slice(0, 6).map((mail) =>
    '<article class="mail"><span class="tag' + (mail.status === "sent" ? " green" : mail.status === "failed" ? " red" : "") + '">' +
    escapeHtml(mailStatus(mail)) + '</span>' +
    '<p><strong>' + escapeHtml(mail.subject) + '</strong></p><p>' + escapeHtml(mail.body) + '</p>' +
    '<p class="muted">' + escapeHtml(formatWhen(mail.at)) + '</p></article>'
  ).join("");
}

function renderAdmin() {
  const user = Store.currentUser();
  const rows = Store.people().map((person) =>
    '<tr><td>' + escapeHtml(person.email) + '</td><td>' + escapeHtml(person.role) + '</td><td>' +
    escapeHtml(person.schoolId || "—") + '</td><td>' +
    (person.mustChangePassword ? '<span class="tag">First login</span>' : '<span class="tag green">Ready</span>') +
    '</td><td>' + (person.role === "admin" ? "" :
      '<button class="btn btn-outline-navy btn-small" data-reset="' + escapeHtml(person.email) + '">New password</button>') +
    '</td></tr>'
  ).join("");

  app.innerHTML = topbar(user) +
    '<main class="page"><h1>People</h1><p class="muted">Add a student or advisor. QueueLy stores the school ID the student already has. It does not create a new one.</p>' +
    (notice ? '<div class="banner"><strong>' + escapeHtml(notice) + '</strong></div>' : '') +
    '<div class="split"><section><table><thead><tr><th>USIU email</th><th>Role</th><th>School ID</th><th>Password</th><th></th></tr></thead><tbody>' +
    rows + '</tbody></table></section><section class="card"><h2>Add a person</h2><form id="add-form">' +
    '<label for="given-name">Given name</label><input id="given-name" placeholder="Ann" required>' +
    '<label for="surname">Surname</label><input id="surname" placeholder="Mchol" required>' +
    '<p class="muted">The USIU email is built from the name. Ann Mchol becomes amchol@usiu.ac.ke.</p>' +
    '<label for="new-role">Role</label><select id="new-role"><option value="student">Student</option><option value="advisor">Course advisor</option></select>' +
    '<div id="school-id-field"><label for="new-id">School ID they already have</label><input id="new-id" inputmode="numeric" placeholder="670375"></div>' +
    '<div id="form-error"></div><button class="btn btn-gold btn-block" type="submit">Email first password</button></form>' +
    '<h3 style="margin-top:22px">Outbox</h3>' + mailList(Store.outbox()) + '</section></div></main>';

  const role = document.querySelector("#new-role");
  const schoolField = document.querySelector("#school-id-field");
  role.addEventListener("change", () => {
    schoolField.hidden = role.value !== "student";
  });
  document.querySelector("#add-form").addEventListener("submit", (event) => {
    event.preventDefault();
    const result = Store.addPerson(
      document.querySelector("#given-name").value,
      document.querySelector("#surname").value,
      role.value,
      role.value === "student" ? document.querySelector("#new-id").value : ""
    );
    if (!result.ok) {
      document.querySelector("#form-error").innerHTML = '<div class="error">' + escapeHtml(result.error) + "</div>";
      return;
    }
    notice = "First password emailed to " + result.email + ". Temporary password " + result.temp + " is in the outbox.";
    renderAdmin();
  });
  document.querySelectorAll("[data-reset]").forEach((button) => {
    button.addEventListener("click", () => {
      const result = Store.resetPassword(button.getAttribute("data-reset"));
      notice = result.ok
        ? "A new temporary password was emailed to " + button.getAttribute("data-reset") + "."
        : result.error;
      renderAdmin();
    });
  });
  bindLogout();
}

function renderAdvisorSchools() {
  const user = Store.currentUser();
  const cards = Store.schools.map((school) =>
    '<button class="card-button" data-school="' + school.id + '"><strong>' + escapeHtml(school.name) + '</strong>' +
    '<p class="muted">Open this school queue</p></button>'
  ).join("");
  app.innerHTML = topbar(user) +
    '<main class="page narrow"><h1>Choose your school</h1><p class="muted">Hospitality sits inside Chandaria School of Business. Pick the school you advise before you open a student.</p>' +
    '<div class="stack">' + cards + '</div></main>';
  document.querySelectorAll("[data-school]").forEach((button) => {
    button.addEventListener("click", () => {
      selectedSchool = button.getAttribute("data-school");
      notice = "";
      renderAdvisorQueue();
    });
  });
  bindLogout();
}

function renderAdvisorQueue() {
  const user = Store.currentUser();
  const school = Store.schoolById(selectedSchool);
  const line = Store.line(selectedSchool);
  const front = line[0];
  const cards = line.length ? line.map((entry, index) =>
    '<article class="card ' + (index === 0 ? "serving" : "") + '"><div class="row"><div><span class="tag navy">' +
    escapeHtml(entry.ticket) + '</span> ' + (index === 0 ? '<span class="tag">' + (entry.status === "serving" ? "Datasheet open" : "At the front") + '</span>' : '<span class="tag">Waiting</span>') +
    '</div><strong>' + escapeHtml(entry.schoolId) + '</strong></div><p><strong>' + escapeHtml(Store.reasonLabel(entry.reason)) + '</strong></p>' +
    '<p class="muted">' + escapeHtml(entry.email) + '</p></article>'
  ).join("") : '<article class="card"><p class="muted">No one is waiting in this school queue.</p></article>';

  app.innerHTML = topbar(user) +
    '<main class="page narrow"><div class="row"><div><h1>' + escapeHtml(school.short) + '</h1><p class="muted">' + line.length + ' in this queue</p></div>' +
    '<button class="btn btn-outline-navy btn-small" id="change-school">Change school</button></div>' +
    (notice ? '<div class="banner"><strong>' + escapeHtml(notice) + '</strong></div>' : '') +
    '<div class="stack">' + cards + '</div>' +
    (front ? '<form id="open-form"><label for="school-id">School ID of the student at the front</label><input id="school-id" inputmode="numeric" placeholder="670375">' +
      '<div id="form-error"></div><button class="btn btn-gold btn-block" type="submit">Open datasheet</button></form>' : '') +
    '</main>';

  document.querySelector("#change-school").addEventListener("click", () => {
    selectedSchool = "";
    notice = "";
    renderAdvisorSchools();
  });
  const form = document.querySelector("#open-form");
  if (form) {
    form.addEventListener("submit", (event) => {
      event.preventDefault();
      const result = Store.openVisit(selectedSchool, document.querySelector("#school-id").value);
      if (!result.ok) {
        document.querySelector("#form-error").innerHTML = '<div class="error">' + escapeHtml(result.error) + "</div>";
        return;
      }
      notice = "";
      renderDatasheet();
    });
  }
  bindLogout();
}

function renderDatasheet() {
  const user = Store.currentUser();
  const school = Store.schoolById(selectedSchool);
  const front = Store.front(selectedSchool);
  if (!front || front.status !== "serving") {
    renderAdvisorQueue();
    return;
  }
  const sheet = Store.datasheet(front.schoolId);
  const courses = sheet.courses.map((course) =>
    '<article class="card"><div class="row"><div><strong>' + escapeHtml(course.code) + '</strong><p class="muted">' +
    escapeHtml(course.title) + ' · ' + escapeHtml(course.time) + '</p></div>' +
    (course.mark ? '<span class="tag ' + (course.mark.indexOf("Drop") === 0 ? "red" : "green") + '">' + escapeHtml(course.mark) + '</span>' : '') +
    '</div>' + (course.registrar ? '<p class="muted">' + escapeHtml(course.registrar) + '</p>' : '') +
    (course.mark === "Drop approved by advisor" ? "" :
      '<button class="btn btn-outline-navy btn-small" data-drop="' + escapeHtml(course.code) + '">Drop course</button>') +
    '</article>'
  ).join("");
  const offered = (Store.catalog[selectedSchool] || []).map((course) =>
    '<option value="' + escapeHtml(course.code) + '">' + escapeHtml(course.code + " · " + course.title + " · " + course.time) + '</option>'
  ).join("");

  app.innerHTML = topbar(user) +
    '<main class="page"><div class="row"><div><h1>Datasheet</h1><p class="muted">' + escapeHtml(school.name) + '</p></div>' +
    '<button class="btn btn-outline-navy btn-small" id="back-queue">Back to queue</button></div>' +
    (notice ? '<div class="banner"><strong>' + escapeHtml(notice) + '</strong></div>' : '') +
    '<section class="card" style="margin-top:16px"><span class="tag navy">' + escapeHtml(front.ticket) + '</span>' +
    '<h2 style="margin-top:8px">' + escapeHtml(sheet.name) + '</h2><p>' + escapeHtml(sheet.schoolId) + ' · ' + escapeHtml(sheet.program) + '</p>' +
    '<p class="muted">' + escapeHtml(sheet.email) + ' · ' + escapeHtml(Store.reasonLabel(front.reason)) + '</p></section>' +
    '<div class="split" style="margin-top:16px"><section><h2>Registered courses</h2><div class="stack">' + courses + '</div></section>' +
    '<section class="card"><h2>Add a course</h2><p class="muted">Only courses offered in this school. A clash or a course already on the sheet is blocked.</p>' +
    '<form id="add-form"><label for="course">Course</label><select id="course">' + offered + '</select>' +
    '<div id="form-error"></div><button class="btn btn-navy btn-block" type="submit">Approve add</button></form>' +
    '<button class="btn btn-gold btn-block" id="close-queue">Close queue</button></section></div></main>';

  document.querySelector("#back-queue").addEventListener("click", () => renderAdvisorQueue());
  document.querySelector("#add-form").addEventListener("submit", (event) => {
    event.preventDefault();
    const result = Store.addCourse(selectedSchool, document.querySelector("#course").value);
    notice = result.ok ? "Add approved. It stays waiting for the registrar." : "";
    if (!result.ok) {
      document.querySelector("#form-error").innerHTML = '<div class="error">' + escapeHtml(result.error) + "</div>";
      return;
    }
    renderDatasheet();
  });
  document.querySelectorAll("[data-drop]").forEach((button) => {
    button.addEventListener("click", () => {
      const result = Store.dropCourse(selectedSchool, button.getAttribute("data-drop"));
      notice = result.ok ? "Drop approved. The course stays on the sheet so the change is still visible." : result.error;
      renderDatasheet();
    });
  });
  document.querySelector("#close-queue").addEventListener("click", () => {
    const result = Store.closeVisit(selectedSchool);
    if (!result.ok) {
      notice = result.error;
      renderDatasheet();
      return;
    }
    const nextText = result.next
      ? " Next is school ID " + result.next.schoolId + "."
      : " No one else is waiting.";
    notice = result.changeText + " recorded and the visit is closed. Sending email to " + result.closed.email + "." + nextText;
    renderAdvisorQueue();
  });
  bindLogout();
}

function renderStudentSchool() {
  const user = Store.currentUser();
  const cards = Store.schools.map((school) =>
    '<button class="card-button' + (selectedSchool === school.id ? " selected" : "") + '" data-school="' + school.id + '"><strong>' +
    escapeHtml(school.name) + '</strong></button>'
  ).join("");
  app.innerHTML = topbar(user) +
    '<main class="page narrow"><h1>Select your school</h1><p class="muted">School ID ' + escapeHtml(user.schoolId) + ' · ' + escapeHtml(user.email) + '</p>' +
    '<div class="stack">' + cards + '</div>' +
    '<button class="btn btn-gold btn-block" id="continue" ' + (selectedSchool ? "" : "disabled") + '>Continue</button></main>';
  document.querySelectorAll("[data-school]").forEach((button) => {
    button.addEventListener("click", () => {
      selectedSchool = button.getAttribute("data-school");
      renderStudentSchool();
    });
  });
  document.querySelector("#continue").addEventListener("click", () => {
    if (selectedSchool) {
      renderStudentReason();
    }
  });
  bindLogout();
}

function renderStudentReason() {
  const user = Store.currentUser();
  const school = Store.schoolById(selectedSchool);
  const cards = Store.reasons.map((reason) =>
    '<button class="card-button' + (selectedReason === reason.id ? " selected" : "") + '" data-reason="' + reason.id + '"><strong>' +
    escapeHtml(reason.label) + '</strong></button>'
  ).join("");
  app.innerHTML = topbar(user) +
    '<main class="page narrow"><button class="btn btn-outline-navy btn-small" id="back-school">Back</button>' +
    '<h1>Purpose of visit</h1><p class="muted">' + escapeHtml(school.name) + '</p><div class="stack">' + cards + '</div>' +
    '<div id="form-error"></div><button class="btn btn-gold btn-block" id="join">Join queue</button>' +
    '<button class="btn btn-outline-navy btn-block" id="history">View history</button></main>';
  document.querySelector("#back-school").addEventListener("click", () => renderStudentSchool());
  document.querySelectorAll("[data-reason]").forEach((button) => {
    button.addEventListener("click", () => {
      selectedReason = button.getAttribute("data-reason");
      renderStudentReason();
    });
  });
  document.querySelector("#join").addEventListener("click", () => {
    if (!selectedReason) {
      document.querySelector("#form-error").innerHTML = '<div class="error">Select a reason to join the queue.</div>';
      return;
    }
    const result = Store.joinQueue(selectedSchool, selectedReason);
    if (!result.ok) {
      document.querySelector("#form-error").innerHTML = '<div class="error">' + escapeHtml(result.error) + "</div>";
      return;
    }
    notice = "You joined the queue. Sending email to " + user.email + ".";
    renderStudentStatus();
  });
  document.querySelector("#history").addEventListener("click", () => renderHistory());
  bindLogout();
}

function renderStudentStatus() {
  const user = Store.currentUser();
  const visit = Store.myVisit();
  if (!visit) {
    selectedSchool = "";
    selectedReason = "";
    renderStudentSchool();
    return;
  }
  const entry = visit.entry;
  const school = Store.schoolById(entry.school);
  const place = entry.status === "serving"
    ? "The advisor has opened your datasheet."
    : (visit.ahead === 0 ? "You are at the front." : visit.ahead + " student" + (visit.ahead === 1 ? "" : "s") + " ahead.");
  app.innerHTML = topbar(user) +
    '<main class="page narrow"><h1>Your queue</h1><section class="card serving"><p class="gold-text">Ticket</p><h2 style="font-size:40px">' +
    escapeHtml(entry.ticket) + '</h2><p>' + escapeHtml(Store.reasonLabel(entry.reason)) + " · " + escapeHtml(school.short) + '</p>' +
    '<p><strong>' + escapeHtml(place) + '</strong></p><p class="muted">Estimated wait ' + visit.wait + ' min · School ID ' + escapeHtml(user.schoolId) + '</p></section>' +
    (notice ? '<div class="banner"><strong>' + escapeHtml(notice) + '</strong></div>' : '') +
    '<section class="card" style="margin-top:16px"><h2>Email for this visit</h2>' + mailList(Store.myMail()) + '</section>' +
    '<div class="actions"><button class="btn btn-outline-navy" id="history">View history</button>' +
    '<button class="btn btn-danger" id="leave">Leave queue</button></div><div id="form-error"></div></main>';
  document.querySelector("#history").addEventListener("click", () => renderHistory());
  document.querySelector("#leave").addEventListener("click", () => {
    const result = Store.leaveQueue();
    if (!result.ok) {
      document.querySelector("#form-error").innerHTML = '<div class="error">' + escapeHtml(result.error) + "</div>";
      return;
    }
    notice = "";
    selectedSchool = "";
    selectedReason = "";
    renderStudentSchool();
  });
  bindLogout();
}

function renderHistory() {
  const user = Store.currentUser();
  const items = Store.myHistory();
  const cards = items.length ? items.map((item) => {
    const changes = item.changes.length
      ? item.changes.map((change) => (change.action === "add" ? "Add " : "Drop ") + change.code).join(", ")
      : "No course change";
    return '<article class="card"><span class="tag navy">' + escapeHtml(item.ticket) + '</span><h2 style="margin-top:8px">' +
      escapeHtml(Store.reasonLabel(item.reason)) + '</h2><p>' + escapeHtml(Store.schoolById(item.school).name) + '</p>' +
      '<p class="muted">' + escapeHtml(changes) + ' · ' + escapeHtml(formatWhen(item.closedAt)) + '</p></article>';
  }).join("") : '<article class="card"><p class="muted">No closed visits yet.</p></article>';
  app.innerHTML = topbar(user) +
    '<main class="page narrow"><button class="btn btn-outline-navy btn-small" id="back">Back</button><h1>Queue history</h1><div class="stack">' +
    cards + '</div></main>';
  document.querySelector("#back").addEventListener("click", () => go());
  bindLogout();
}

window.addEventListener("queuely-mail", (event) => {
  const field = document.activeElement;
  const typing = field && (field.tagName === "INPUT" || field.tagName === "SELECT" || field.tagName === "TEXTAREA");
  if (!typing && event.detail) {
    if (event.detail.status === "sent") {
      notice = "Sent to " + event.detail.to + ".";
    } else if (event.detail.status === "failed") {
      notice = "Not sent to " + event.detail.to + ". " + (event.detail.detail || "");
    }
    go();
  }
});

go();
