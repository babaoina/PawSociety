(function () {
    if (window.AdminPopup) return;

    const style = document.createElement('style');
    style.textContent = `
        .admin-popup-overlay{position:fixed;inset:0;background:rgba(27,22,18,.45);display:flex;align-items:center;justify-content:center;padding:20px;z-index:10000;opacity:0;pointer-events:none;transition:opacity .18s ease}
        .admin-popup-overlay.show{opacity:1;pointer-events:auto}
        .admin-popup-card{width:min(460px,100%);background:#fffdf9;border:1px solid rgba(84,60,37,.10);border-radius:20px;box-shadow:0 18px 40px rgba(48,33,20,.18);padding:22px 22px 18px;transform:translateY(8px);transition:transform .18s ease;color:#2e261f}
        .admin-popup-overlay.show .admin-popup-card{transform:translateY(0)}
        .admin-popup-title{margin:0 0 10px;font-size:20px;font-weight:800;color:#2e261f}
        .admin-popup-message{margin:0;color:#6d6257;font-size:14px;line-height:1.65;white-space:pre-wrap}
        .admin-popup-actions{display:flex;justify-content:flex-end;gap:10px;margin-top:20px}
        .admin-popup-btn{border:none;border-radius:12px;padding:10px 16px;font-size:14px;font-weight:700;cursor:pointer;transition:transform .15s ease,opacity .15s ease}
        .admin-popup-btn:hover{transform:translateY(-1px)}
        .admin-popup-btn-secondary{background:#f2ece4;color:#5c4d3f}
        .admin-popup-btn-primary{background:#8a5a33;color:#fffaf4}
        .admin-popup-btn-danger{background:#b44738;color:#fff6f4}
    `;
    document.head.appendChild(style);

    function showPopup({ title, message, confirmText = 'OK', cancelText = '', danger = false }) {
        return new Promise((resolve) => {
            const overlay = document.createElement('div');
            overlay.className = 'admin-popup-overlay';

            const card = document.createElement('div');
            card.className = 'admin-popup-card';

            card.innerHTML = `
                <h3 class="admin-popup-title">${title || 'Notice'}</h3>
                <p class="admin-popup-message"></p>
                <div class="admin-popup-actions"></div>
            `;
            card.querySelector('.admin-popup-message').textContent = message || '';
            const actions = card.querySelector('.admin-popup-actions');

            function close(result) {
                overlay.classList.remove('show');
                setTimeout(() => overlay.remove(), 180);
                resolve(result);
            }

            if (cancelText) {
                const cancelBtn = document.createElement('button');
                cancelBtn.className = 'admin-popup-btn admin-popup-btn-secondary';
                cancelBtn.textContent = cancelText;
                cancelBtn.onclick = () => close(false);
                actions.appendChild(cancelBtn);
            }

            const confirmBtn = document.createElement('button');
            confirmBtn.className = `admin-popup-btn ${danger ? 'admin-popup-btn-danger' : 'admin-popup-btn-primary'}`;
            confirmBtn.textContent = confirmText;
            confirmBtn.onclick = () => close(true);
            actions.appendChild(confirmBtn);

            overlay.addEventListener('click', (event) => {
                if (event.target === overlay && cancelText) close(false);
            });

            overlay.appendChild(card);
            document.body.appendChild(overlay);
            requestAnimationFrame(() => overlay.classList.add('show'));
        });
    }

    window.AdminPopup = {
        alert(message, options = {}) {
            return showPopup({
                title: options.title || 'Notice',
                message,
                confirmText: options.confirmText || 'OK',
                danger: !!options.danger
            });
        },
        confirm(message, options = {}) {
            return showPopup({
                title: options.title || 'Confirm Action',
                message,
                confirmText: options.confirmText || 'Confirm',
                cancelText: options.cancelText || 'Cancel',
                danger: !!options.danger
            });
        }
    };
})();
