let courses = [];

async function saveConfig() {
    const cookie = document.getElementById('cookie').value;
    const mp4Dir = document.getElementById('mp4Dir').value;
    const downloadType = document.getElementById('downloadType').value;

    const params = new URLSearchParams({cookie, mp4Dir, downloadType});
    const res = await fetch('/api/config', {method: 'POST', body: params});
    if (res.ok) alert('配置已保存');
}

async function loadCourses() {
    const res = await fetch('/api/courses');
    courses = await res.json();
    renderTable();
}

function renderTable() {
    const tbody = document.getElementById('courseBody');
    if (courses.length === 0) {
        tbody.innerHTML = '<tr><td colspan="6" class="empty">没有找到课程</td></tr>';
        return;
    }
    tbody.innerHTML = courses.map((c, i) => {
        const statusClass = c.status === '已下载' ? 'status-downloaded'
            : c.status === '部分' ? 'status-partial' : 'status-not';
        return `<tr>
            <td><input type="checkbox" class="course-check" value="${c.courseId}" data-status="${c.status}"></td>
            <td>${i + 1}</td>
            <td>${c.courseName}</td>
            <td>${c.type}</td>
            <td class="${statusClass}">${c.status}</td>
            <td>${c.localSize}</td>
        </tr>`;
    }).join('');
}

function toggleAll(el) {
    document.querySelectorAll('.course-check').forEach(cb => cb.checked = el.checked);
}

function selectAll() {
    document.querySelectorAll('.course-check').forEach(cb => cb.checked = true);
    document.getElementById('checkAll').checked = true;
}

function invertSelect() {
    document.querySelectorAll('.course-check').forEach(cb => cb.checked = !cb.checked);
}

function selectUndownloaded() {
    document.querySelectorAll('.course-check').forEach(cb => {
        cb.checked = cb.dataset.status !== '已下载';
    });
}

async function startDownload() {
    const selected = [...document.querySelectorAll('.course-check:checked')].map(cb => cb.value);
    if (selected.length === 0) { alert('请先选择课程'); return; }

    await fetch('/api/download', {
        method: 'POST',
        headers: {'Content-Type': 'application/json'},
        body: JSON.stringify(selected)
    });

    document.getElementById('progressPanel').style.display = 'block';
    connectSSE();
}

function connectSSE() {
    const evtSource = new EventSource('/api/progress/stream');
    evtSource.addEventListener('progress', function(e) {
        const data = JSON.parse(e.data);
        renderProgress(data);
    });
    evtSource.onerror = () => evtSource.close();
}

function renderProgress(progressList) {
    const container = document.getElementById('progressList');
    container.innerHTML = progressList.map(p => {
        const pct = p.total > 0 ? Math.round(p.completed / p.total * 100) : 0;
        const stateColor = p.state === '完成' ? '#52c41a' : p.state === '失败' ? '#ff4d4f' : '#1677ff';
        return `<div class="progress-item">
            <div class="progress-label">
                <span>${p.courseName || p.courseId}</span>
                <span>${p.state} ${p.completed}/${p.total}</span>
            </div>
            <div class="progress-bar-bg">
                <div class="progress-bar-fill" style="width:${pct}%; background:${stateColor}">${pct}%</div>
            </div>
            ${p.error ? '<div style="color:#ff4d4f;font-size:12px;margin-top:2px">' + p.error + '</div>' : ''}
        </div>`;
    }).join('');
}
