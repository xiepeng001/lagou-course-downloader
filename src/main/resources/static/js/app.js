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
        tbody.innerHTML = '<tr><td colspan="6" class="empty">没有找到课程</td></tr>';
        return;
    }
    tbody.innerHTML = courses.map((c, i) => `
        <tr class="course-row">
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
            <td colspan="6" class="lesson-container"></td>
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
    if (container.querySelector('.lesson-tree')) return;
    container.innerHTML = '<div class="lesson-loading">加载中...</div>';

    try {
        const res = await fetch(`/api/courses/${courseId}/lessons?courseType=${encodeURIComponent(courseType)}`);
        const lessons = await res.json();
        if (lessons.length === 0) {
            container.innerHTML = '<div style="padding:8px;color:#999">暂无课时信息</div>';
            return;
        }
        const tree = buildTree(lessons);
        container.innerHTML = renderTree(tree, courseId);
    } catch (e) {
        container.innerHTML = '<div style="padding:8px;color:#ff4d4f">加载失败</div>';
    }
}

// Build nested tree from flat lesson list
function buildTree(lessons) {
    const root = {children: []};
    const pathMap = {'': root};

    for (const l of lessons) {
        const isGroup = !l.lessonId;
        const node = {
            id: l.lessonId || '',
            name: l.lessonName,
            type: l.type,
            downloaded: l.downloaded,
            level: l.level,
            groupPath: l.groupPath,
            isGroup: isGroup,
            children: [],
            checked: false
        };
        const parentKey = l.groupPath || '';
        const parent = pathMap[parentKey] || root;
        parent.children.push(node);
        // Groups become parents for the next level
        if (isGroup) {
            const key = l.groupPath ? l.groupPath + '/' + l.lessonName : l.lessonName;
            pathMap[key] = node;
        }
    }
    return root;
}

function renderTree(node, courseId) {
    let html = '<div class="lesson-tree">';
    for (const child of node.children) {
        html += renderTreeNode(child, courseId);
    }
    html += '</div>';
    return html;
}

function renderTreeNode(node, courseId) {
    const indent = node.level * 20;
    const nodeId = node.isGroup ? 'group-' + courseId + '-' + node.level + '-' + Math.random().toString(36).substr(2, 6) : 'lesson-' + courseId + '-' + node.id;
    node._nodeId = nodeId;

    if (node.isGroup) {
        const hasChildren = node.children.length > 0;
        const expandBtn = hasChildren ? `<span class="tree-toggle" onclick="treeToggle(this)">&#9660;</span>` : '<span class="tree-toggle-placeholder"></span>';
        let html = `<div class="tree-node tree-group level-${node.level}" style="padding-left:${indent}px">
            ${expandBtn}
            <input type="checkbox" class="tree-check" id="${nodeId}" data-node-id="${nodeId}" onchange="treeCheck(this)">
            <label for="${nodeId}"><span class="tree-group-name">${node.name}</span></label>
        </div>`;
        if (hasChildren) {
            html += `<div class="tree-children">`;
            for (const child of node.children) {
                html += renderTreeNode(child, courseId);
            }
            html += `</div>`;
        }
        return html;
    } else {
        return `<div class="tree-node tree-leaf level-${node.level}" style="padding-left:${indent + 22}px">
            <input type="checkbox" class="tree-check" id="${nodeId}" data-lesson-id="${node.id}" data-course-id="${courseId}" ${node.downloaded ? 'data-downloaded="true"' : ''} onchange="leafCheck(this)">
            <label for="${nodeId}">
                <span class="tree-leaf-name">${node.name}</span>
                <span class="tree-leaf-type">${node.type || ''}</span>
                <span class="tree-leaf-status">${node.downloaded ? '✓' : ''}</span>
            </label>
        </div>`;
    }
}

// Toggle tree node expand/collapse
function treeToggle(btn) {
    const children = btn.closest('.tree-node').nextElementSibling;
    if (!children || !children.classList.contains('tree-children')) return;
    if (children.style.display === 'none') {
        children.style.display = '';
        btn.innerHTML = '&#9660;';
    } else {
        children.style.display = 'none';
        btn.innerHTML = '&#9654;';
    }
}

// Group checkbox: toggle all descendant leaf checkboxes
function treeCheck(cb) {
    const groupNode = cb.closest('.tree-node');
    const children = groupNode.nextElementSibling;
    if (!children || !children.classList.contains('tree-children')) return;
    const leaves = children.querySelectorAll('.tree-leaf .tree-check');
    leaves.forEach(lc => lc.checked = cb.checked);
    // Also check sub-groups
    const subGroups = children.querySelectorAll('.tree-group > .tree-check');
    subGroups.forEach(gc => {
        gc.checked = cb.checked;
        gc.dispatchEvent(new Event('change'));
    });
}

// Leaf checkbox: update parent group check state
function leafCheck(cb) {
    const childrenContainer = cb.closest('.tree-children');
    if (!childrenContainer) return;
    const allLeaves = childrenContainer.querySelectorAll(':scope > .tree-leaf .tree-check');
    const parentCheck = childrenContainer.previousElementSibling?.querySelector('.tree-check');
    if (parentCheck && allLeaves.length > 0) {
        parentCheck.checked = [...allLeaves].every(l => l.checked);
    }
}

// Collect all selected lesson IDs (leaf nodes only)
function getSelectedLessonIds() {
    return [...document.querySelectorAll('.tree-leaf .tree-check:checked')].map(cb => cb.dataset.lessonId);
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
    // Collect from course-level checkboxes
    const courseIds = [...document.querySelectorAll('.course-check:checked')].map(cb => cb.value);
    // Collect from lesson-level checkboxes
    const lessonIds = getSelectedLessonIds();

    if (courseIds.length === 0 && lessonIds.length === 0) { alert('请先选择课程或课时'); return; }

    await fetch('/api/download', {
        method: 'POST',
        headers: {'Content-Type': 'application/json'},
        body: JSON.stringify({courseIds, lessonIds})
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
