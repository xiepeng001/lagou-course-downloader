let courses = [];

async function saveConfig() {
    const mp4Dir = document.getElementById('mp4Dir').value;
    const downloadType = document.getElementById('downloadType').value;

    const params = new URLSearchParams({mp4Dir, downloadType});
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
        tbody.innerHTML = '<tr><td colspan="7" class="empty">没有找到课程</td></tr>';
        return;
    }
    tbody.innerHTML = courses.map((c, i) => `
        <tr class="course-row" data-course-id="${c.courseId}" data-course-type="${c.type}">
            <td><input type="checkbox" class="course-check" value="${c.courseId}" data-status="${c.status}"></td>
            <td>${i + 1}</td>
            <td>
                <span class="expand-btn" onclick="toggleLessons('${c.courseId}', '${c.type}', this)">&#9654;</span>
                ${c.courseName}
            </td>
            <td>${c.type}</td>
            <td class="${c.status === '已下载' ? 'status-downloaded' : c.status === '部分' ? 'status-partial' : 'status-not'}">${c.status}</td>
            <td>${c.localSize}</td>
        </tr>
        <tr class="lesson-row" id="lessons-${c.courseId}" style="display:none">
            <td colspan="6" class="lesson-container"><div class="lesson-loading">加载中...</div></td>
        </tr>
    `).join('');
}

async function toggleLessons(courseId, courseType, btn) {
    const lessonRow = document.getElementById('lessons-' + courseId);
    if (lessonRow.style.display !== 'none') {
        lessonRow.style.display = 'none';
        btn.innerHTML = '&#9654;';
        return;
    }

    lessonRow.style.display = '';
    btn.innerHTML = '&#9660;';

    const container = lessonRow.querySelector('.lesson-container');
    if (container.querySelector('.lesson-loaded')) return;

    try {
        const res = await fetch(`/api/courses/${courseId}/lessons?courseType=${encodeURIComponent(courseType)}`);
        const lessons = await res.json();
        if (lessons.length === 0) {
            container.innerHTML = '<div class="lesson-loaded" style="padding:8px;color:#999">暂无课时信息</div>';
            return;
        }
        container.innerHTML = `<div class="lesson-loaded">
            <table class="lesson-table">
                <thead><tr><th>课时名称</th><th>类型</th><th>状态</th><th>已下载</th></tr></thead>
                <tbody>${lessons.map(l => `<tr>
                    <td>${l.lessonName}</td>
                    <td>${l.type}</td>
                    <td>${l.status}</td>
                    <td>${l.downloaded ? '✓' : '—'}</td>
                </tr>`).join('')}</tbody>
            </table>
        </div>`;
    } catch (e) {
        container.innerHTML = '<div class="lesson-loaded" style="padding:8px;color:#ff4d4f">加载失败</div>';
    }
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
