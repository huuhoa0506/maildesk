(() => {
    const bodyTemplate = document.getElementById("body-template");
    const subjectTemplate = document.getElementById("subject-template");
    const previewSubject = document.getElementById("preview-subject");
    const previewBody = document.getElementById("preview-body");
    const sampleValues = new Map(
        [...document.querySelectorAll("[data-sample-column]")].map((column) => [
            column.dataset.sampleColumn,
            column.parentElement.querySelector("[data-sample-value]")?.dataset.sampleValue ?? ""
        ])
    );
    let activeTemplateField = bodyTemplate;

    const normalizeColumn = (name) => {
        const decoder = document.createElement("textarea");
        decoder.innerHTML = name;
        return decoder.value.normalize("NFD").replace(/\p{M}/gu, "")
            .toLocaleLowerCase("vi").replace(/[^a-z0-9]+/g, " ").trim();
    };

    const renderPreview = () => {
        if (!bodyTemplate || !subjectTemplate) return;
        const render = (text) => text.replace(/\{\{\s*([^{}]+?)\s*}}/g, (match, key) => {
            const column = [...sampleValues.keys()].find((candidate) =>
                normalizeColumn(candidate) === normalizeColumn(key));
            return column ? sampleValues.get(column) : `[Không có cột: ${key.trim()}]`;
        });
        previewSubject.textContent = render(subjectTemplate.value);
        previewBody.textContent = render(bodyTemplate.value);
    };

    document.querySelectorAll(".variable-chip").forEach((button) => {
        button.addEventListener("click", () => {
            const field = activeTemplateField ?? bodyTemplate;
            const token = `{{${button.dataset.variable}}}`;
            const start = field.selectionStart ?? field.value.length;
            const end = field.selectionEnd ?? start;
            field.setRangeText(token, start, end, "end");
            field.focus();
            renderPreview();
        });
    });
    bodyTemplate?.addEventListener("focus", () => { activeTemplateField = bodyTemplate; });
    subjectTemplate?.addEventListener("focus", () => { activeTemplateField = subjectTemplate; });
    bodyTemplate?.addEventListener("input", renderPreview);
    subjectTemplate?.addEventListener("input", renderPreview);
    renderPreview();

    const rows = [...document.querySelectorAll(".email-row")];
    const selectAll = document.getElementById("select-all");
    const sendButton = document.getElementById("send-button");
    const selectedCount = document.getElementById("selected-count");
    const searchInput = document.getElementById("search-input");
    const statusFilter = document.getElementById("status-filter");
    const dateFilter = document.getElementById("date-filter");
    const filteredEmptyState = document.getElementById("filtered-empty-state");
    const progressPanel = document.getElementById("mail-job-progress");

    const updateAgentStatuses = (agents) => {
        const statusLabels = {
            pending: ["Chưa gửi", "pill-pending"],
            sending: ["Đang gửi", "pill-pending"],
            sent: ["Đã gửi", "pill-sent"],
            failed: ["Gửi lỗi", "pill-failed"]
        };
        agents.forEach((agent) => {
            const row = rows.find((candidate) =>
                candidate.querySelector(".row-checkbox")?.value === String(agent.id));
            if (!row) return;
            row.dataset.status = agent.status;
            const pill = row.querySelector(".status-pill");
            const [label, style] = statusLabels[agent.status] ?? statusLabels.pending;
            pill.className = `status-pill ${style}`;
            pill.querySelector("span").textContent = label;
            row.querySelector(".error-cell").textContent = agent.errorMessage ?? "—";
            row.querySelector(".row-checkbox").disabled = agent.status === "sent" || agent.status === "sending";
        });
        const counts = agents.reduce((result, agent) => {
            if (agent.status === "sent") result.sent++;
            else if (agent.status === "failed") result.failed++;
            else result.pending++;
            return result;
        }, { sent: 0, failed: 0, pending: 0 });
        document.getElementById("agent-total-count").textContent = agents.length;
        document.getElementById("agent-pending-count").textContent = counts.pending;
        document.getElementById("agent-sent-count").textContent = counts.sent;
        document.getElementById("agent-failed-count").textContent = counts.failed;
        applyFilters();
    };

    const updateSelection = () => {
        if (!selectAll || !sendButton || !selectedCount) return;
        const visible = rows.filter((row) => !row.hidden);
        const selectable = visible.filter((row) => !row.querySelector(".row-checkbox").disabled);
        const selected = rows.filter((row) => row.querySelector(".row-checkbox").checked);
        const visibleSelected = selectable.filter((row) => row.querySelector(".row-checkbox").checked);
        selectedCount.textContent = selected.length;
        sendButton.disabled = selected.length === 0 || document.getElementById("send-form")?.dataset.busy === "true";
        selectAll.checked = selectable.length > 0 && visibleSelected.length === selectable.length;
        selectAll.indeterminate = visibleSelected.length > 0 && visibleSelected.length < selectable.length;
    };

    const applyFilters = () => {
        if (!searchInput || !statusFilter) return;
        const query = searchInput.value.trim().toLocaleLowerCase("vi");
        const status = statusFilter.value;
        const importedDate = dateFilter?.value ?? "";
        rows.forEach((row) => {
            row.hidden = !((status === "all" || row.dataset.status === status)
                && row.dataset.search.toLocaleLowerCase("vi").includes(query)
                && (!importedDate || row.dataset.date === importedDate));
        });
        const visible = rows.filter((row) => !row.hidden).length;
        if (filteredEmptyState) filteredEmptyState.hidden = visible !== 0 || rows.length === 0;
        updateSelection();
    };

    searchInput?.addEventListener("input", applyFilters);
    statusFilter?.addEventListener("change", applyFilters);
    dateFilter?.addEventListener("change", applyFilters);
    rows.forEach((row) => row.querySelector(".row-checkbox")?.addEventListener("change", updateSelection));
    selectAll?.addEventListener("change", () => {
        rows.filter((row) => !row.hidden).forEach((row) => {
            const checkbox = row.querySelector(".row-checkbox");
            if (!checkbox.disabled) checkbox.checked = selectAll.checked;
        });
        updateSelection();
    });
    document.getElementById("send-form")?.addEventListener("submit", (event) => {
        const count = rows.filter((row) => row.querySelector(".row-checkbox").checked).length;
        if (count === 0 || !window.confirm(`Xác nhận gửi email đến ${count} học sinh?`)) event.preventDefault();
    });

    if (progressPanel?.dataset.active === "true") {
        const pollProgress = async () => {
            try {
                const response = await fetch(progressPanel.dataset.progressUrl, { headers: { Accept: "application/json" } });
                if (!response.ok) throw new Error(`Không tải được tiến độ (${response.status}).`);
                const progress = await response.json();
                const percent = progress.total === 0 ? 100 : Math.floor(progress.processed * 100 / progress.total);
                document.getElementById("job-processed").textContent = progress.processed;
                document.getElementById("job-total").textContent = progress.total;
                document.getElementById("job-succeeded").textContent = progress.succeeded;
                document.getElementById("job-failed").textContent = progress.failed;
                document.getElementById("job-progress-percent").textContent = `${percent}%`;
                document.getElementById("job-progress-bar").style.width = `${percent}%`;
                if (progress.status === "completed") {
                    if (Array.isArray(progress.agents)) updateAgentStatuses(progress.agents);
                    document.getElementById("job-state").textContent = "Lượt gửi gần nhất đã hoàn tất";
                    progressPanel.dataset.active = "false";
                    const form = document.getElementById("send-form");
                    if (form) form.dataset.busy = "false";
                    if (selectAll) selectAll.disabled = false;
                    updateSelection();
                    return;
                }
                window.setTimeout(pollProgress, 1000);
            } catch (error) {
                console.error("Không thể cập nhật tiến độ gửi email.", error);
                window.setTimeout(pollProgress, 3000);
            }
        };
        window.setTimeout(pollProgress, 1000);
    }
    applyFilters();
})();
