document.addEventListener("DOMContentLoaded", (e) => {
    const data = JSON.parse(document.getElementById('verification-params').textContent);
    document.getElementById('amount').innerText = data.amountSats;
    document.getElementById('recipient').innerText = data.recipient;

    const confirmBtn = document.getElementById('confirmBtn');
    const cancelBtn = document.getElementById('cancelBtn');

    // Set up confirm button
    confirmBtn.addEventListener('click', function() {
        if (data.confirmToken) {
            window.location.href = `/api/tx-verify/confirm?token=${data.confirmToken}`;
        }
    });

    // Set up cancel button
    cancelBtn.addEventListener('click', function() {
        if (data.cancelToken) {
            window.location.href = `/api/tx-verify/cancel?token=${data.cancelToken}`;
        }
    });
});
