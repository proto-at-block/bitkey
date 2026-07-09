document.addEventListener('DOMContentLoaded', function () {
    const params = JSON.parse(document.getElementById('privileged-action-params').textContent);
    const { privilegedActionType, attemptsRemaining: initialAttempts, lockoutAtLoad } = params;
    let attemptsRemaining = typeof initialAttempts === 'number' ? initialAttempts : null;

    const urlParams = new URLSearchParams(window.location.search);
    const webAuthToken = urlParams.get('web_auth_token');

    const loadingOverlay = document.getElementById('loadingOverlay');
    const mainContent = document.getElementById('mainContent');

    const serialEntry = document.getElementById('serialEntry');
    const successScreen = document.getElementById('successScreen');
    const cancellationScreen = document.getElementById('cancellationScreen');
    const lockoutScreen = document.getElementById('lockoutScreen');

    const serialButtons = document.getElementById('serialButtons');
    const supportButtons = document.getElementById('supportButtons');

    const serialInput = document.getElementById('serialInput');
    const errorMessage = document.getElementById('errorMessage');
    const attemptsHint = document.getElementById('attemptsHint');

    const approveBtn = document.getElementById('approveBtn');
    const cancelBtn = document.getElementById('cancelBtn');

    function renderAttemptsRemaining() {
        if (attemptsRemaining === null) {
            attemptsHint.style.display = 'none';
            return;
        }
        attemptsHint.textContent =
            attemptsRemaining === 1
                ? '1 attempt remaining.'
                : `${attemptsRemaining} attempts remaining.`;
        attemptsHint.style.display = 'block';
    }

    function showError(message) {
        errorMessage.textContent = message;
        errorMessage.style.display = 'block';
    }

    function clearError() {
        errorMessage.textContent = '';
        errorMessage.style.display = 'none';
    }

    function showSuccessScreen() {
        serialEntry.classList.remove('active');
        serialButtons.style.opacity = '0';
        setTimeout(function () {
            serialButtons.style.display = 'none';
            supportButtons.style.display = 'flex';
            successScreen.classList.add('active');
            setTimeout(function () {
                supportButtons.style.opacity = '1';
            }, 50);
        }, 300);
    }

    function showCancellationScreen() {
        serialEntry.classList.remove('active');
        serialButtons.style.opacity = '0';
        setTimeout(function () {
            serialButtons.style.display = 'none';
            supportButtons.style.display = 'flex';
            cancellationScreen.classList.add('active');
            setTimeout(function () {
                supportButtons.style.opacity = '1';
            }, 50);
        }, 300);
    }

    function showLockoutScreen() {
        serialEntry.classList.remove('active');
        serialButtons.style.opacity = '0';
        setTimeout(function () {
            serialButtons.style.display = 'none';
            supportButtons.style.display = 'flex';
            lockoutScreen.classList.add('active');
            setTimeout(function () {
                supportButtons.style.opacity = '1';
            }, 50);
        }, 300);
    }

    async function submitSerial() {
        clearError();
        const serial = serialInput.value.trim();
        if (serial.length === 0) {
            showError('Please enter a serial number.');
            return;
        }
        if (!webAuthToken) {
            showError('Missing verification token. Please re-open the link from your email.');
            return;
        }
        approveBtn.disabled = true;
        cancelBtn.disabled = true;
        try {
            const response = await fetch('/api/privileged-action/respond', {
                method: 'PUT',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    action: 'CONFIRM',
                    privileged_action_type: privilegedActionType,
                    web_auth_token: webAuthToken,
                    submission_data: { serial }
                })
            });
            if (response.ok) {
                showSuccessScreen();
                return;
            }
            if (response.status === 422) {
                // detail is `{"remainingAttempts": N}` on serial mismatch.
                let remaining = null;
                try {
                    const body = await response.json();
                    const detail = body && body.errors && body.errors[0] && body.errors[0].detail;
                    if (detail) {
                        const parsed = JSON.parse(detail);
                        if (typeof parsed.remainingAttempts === 'number') {
                            remaining = parsed.remainingAttempts;
                        }
                    }
                } catch (_e) { /* leave remaining = null */ }
                if (remaining !== null) {
                    attemptsRemaining = remaining;
                    renderAttemptsRemaining();
                }
                showError("That serial doesn't match. Double-check the number on your device and try again.");
                serialInput.value = '';
                serialInput.focus();
                return;
            }
            if (response.status === 410) {
                showLockoutScreen();
                return;
            }
            showError('Something went wrong. Please try again.');
        } catch (e) {
            console.error('Error submitting serial:', e);
            showError('Network error. Please try again.');
        } finally {
            approveBtn.disabled = false;
            cancelBtn.disabled = false;
        }
    }

    async function cancelVerification() {
        if (!webAuthToken) {
            showCancellationScreen();
            return;
        }
        approveBtn.disabled = true;
        cancelBtn.disabled = true;
        try {
            await fetch('/api/privileged-action/respond', {
                method: 'PUT',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    action: 'CANCEL',
                    web_auth_token: webAuthToken
                })
            });
        } catch (e) {
            console.error('Error cancelling verification:', e);
        } finally {
            showCancellationScreen();
        }
    }

    approveBtn.addEventListener('click', submitSerial);
    cancelBtn.addEventListener('click', cancelVerification);
    serialInput.addEventListener('keydown', function (e) {
        if (e.key === 'Enter') {
            e.preventDefault();
            submitSerial();
        }
    });

    renderAttemptsRemaining();

    setTimeout(function () {
        loadingOverlay.style.opacity = '0';
        mainContent.classList.add('active');
        setTimeout(function () {
            loadingOverlay.style.display = 'none';
        }, 500);
        if (lockoutAtLoad) {
            // Server already terminated this session (max-attempts or
            // expiry). Skip the entry screen.
            serialButtons.style.display = 'none';
            supportButtons.style.display = 'flex';
            supportButtons.style.opacity = '1';
            lockoutScreen.classList.add('active');
        } else {
            serialEntry.classList.add('active');
            serialInput.focus();
        }
    }, 1500);
});
