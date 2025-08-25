document.addEventListener('DOMContentLoaded', function() {    
    const data = JSON.parse(document.getElementById('privileged-action-params').textContent);
    const {txPolicyVerification = {}, privilegedActionType} = data;
    const {threshold} = txPolicyVerification;
    const {amount_fiat : amountFiat, amount_sats : amountSats, currency_code : amountCurrency} = threshold;
    // Extract web_auth_token from URL parameters safely
    const urlParams = new URLSearchParams(window.location.search);
    const webAuthToken = urlParams.get('web_auth_token');

    const formattedAmountFiat = formatCurrency(amountFiat, amountCurrency);    
    const roundedAmountFiat = roundFiatAmount(amountFiat, amountCurrency);

    const amountScreen = document.getElementById('amountConfirmation');
    const cancellationScreen = document.getElementById('cancellationScreen');
    const successScreen = document.getElementById('successScreen');
    const amountButtons = document.getElementById('amountButtons');
    const supportButtons = document.getElementById('supportButtons');
    
    // Function to populate amount elements from data attributes
    function populateAmountElements() {        
        // Set data attributes for original amounts
        document.querySelectorAll('.amountFiat').forEach(el => {
            el.setAttribute('data-amount-fiat', formattedAmountFiat);
            el.textContent = formattedAmountFiat;
        });
        // Set data attributes for original amounts
        document.querySelectorAll('.amountFiatRounded').forEach(el => {
            el.setAttribute('data-amount-fiat-rounded', roundedAmountFiat);
            el.textContent = roundedAmountFiat;
        });        
        document.querySelectorAll('.amountSats').forEach(el => {
            const satsText = `${amountSats.toLocaleString()} sats`;
            el.setAttribute('data-amount-sats', satsText);
            el.textContent = satsText;
        });
        document.querySelectorAll('.amountCurrency').forEach(el => {
            el.setAttribute('data-amount-currency', amountCurrency);
            el.textContent = amountCurrency;
        });
    }    

    // Populate the amount elements
    populateAmountElements();

    // Show loading screen first
    const loadingOverlay = document.getElementById('loadingOverlay');
    const mainContent = document.getElementById('mainContent');

    /**
     * Format currency amount with optional rounding
     * @param {number} amount - The amount to format
     * @param {string} currencyCode - The currency code (e.g., 'USD', 'BTC', 'XXX')
     * @param {Object} options - Formatting options
     * @param {boolean} options.round - Whether to round amounts above 100 to whole numbers
     * @returns {string} Formatted currency string
     */
    function formatCurrency(amount, currencyCode, options = {}) {
        if (amount === undefined || currencyCode === undefined) {
            return '0';
        }

        // Convert from fractional amount only for USD (e.g., 205 cents = 2.05 dollars)
        const actualAmount = currencyCode.toUpperCase() === 'USD' ? amount / 100 : amount;
        
        // Apply rounding if requested and amount is above threshold
        const finalAmount = options.round && actualAmount > 100 ? Math.round(actualAmount) : actualAmount;

        // Custom formatting for CAD and AUD
        if (currencyCode.toUpperCase() === 'AUD' || currencyCode.toUpperCase() === 'CAD') {
            return `$${finalAmount}`;
        }        
        
        // Custom formatting for BTC and XXX
        if (currencyCode.toUpperCase() === 'BTC') {
            return `₿${finalAmount} BTC`;
        }
        
        if (currencyCode.toUpperCase() === 'XXX') {
            return `XX${finalAmount} XXX`;
        }
        
        // Standard formatting for other currencies
        try {
          return new Intl.NumberFormat(undefined, {
            style: "currency",
            currency: currencyCode,
            currencyDisplay: "symbol",
          }).format(finalAmount);
        } catch (e) {
          // fallback for unsupported currency codes
          return `${finalAmount} ${currencyCode}`;
        }
    }

    // Function to round fiat amount based on value thresholds
    function roundFiatAmount(amount, currencyCode) {
        return formatCurrency(amount, currencyCode, { round: true });
    }
    
    // Function to show the amount confirmation screen
    function showAmountConfirmation() {        
        // Show amount screen, hide others
        amountScreen.classList.add('active');
        successScreen.classList.remove('active');
        
        amountButtons.style.display = 'flex';
        amountButtons.style.opacity = '1';
        
        // Set up button handlers
        const yesButton = document.getElementById('yesBtn');
        const noButton = document.getElementById('noBtn');
        
        // Add new event listeners
        yesButton.addEventListener('click', function() {
            // Transition to verification screen with animation
            amountScreen.classList.remove('active');
            amountButtons.style.opacity = '0';
            
            setTimeout(function() {
                amountButtons.style.display = 'none';
                
                setTimeout(function() {             
                    // Initialize the address verification screen
                    confirmLimitVerification();
                }, 50);
            }, 300);
        });
        
        noButton.addEventListener('click', function() {
            cancelTransaction();
        });
    }
    
    // Function to handle transaction cancellation API call
    function cancelTransaction() {
        if (webAuthToken) {
            // Create a Promise for the PUT request
            const cancelTransactionPromise = new Promise((resolve, reject) => {
                fetch(`/api/privileged-action/respond`, {
                    method: 'PUT',
                    headers: {
                        'Content-Type': 'application/json'
                    },
                    body: JSON.stringify({
                        action: 'CANCEL',
                        web_auth_token: webAuthToken
                    })
                })
                .then(response => {
                    if (!response.ok) {
                        throw new Error('Network response was not ok');
                    }
                    resolve(response);
                })
                .catch(error => {
                    console.error('Error:', error);
                    reject(error);
                });
            });

            // Handle the response
            cancelTransactionPromise
                .then(() => {
                    showCancellationScreen();
                })
                .catch(error => {
                    console.error('Error cancelling transaction:', error);
                    showCancellationScreen();
                });
        } else {
            showCancellationScreen();
        }
    }

    // Function to show the cancellation screen
    function showCancellationScreen() {        
        // Transition to canceled screen with animation
        amountScreen.classList.remove('active');
        amountButtons.style.opacity = '0';
        
        setTimeout(function() {
            amountButtons.style.display = 'none';
            supportButtons.style.display = 'flex';
            cancellationScreen.classList.add('active');
            
            setTimeout(function() {
                // Show support buttons
                supportButtons.style.display = 'flex';
                supportButtons.style.opacity = '1';
            }, 50);
        }, 300);
    }
    
    function confirmLimitVerification() {
        // Transition to success screen
        if (webAuthToken) {
            // Create a Promise for the PUT request
            const confirmTransaction = new Promise((resolve, reject) => {
                fetch(`/api/privileged-action/respond`, {
                    method: 'PUT',
                    headers: {
                        'Content-Type': 'application/json'
                    },
                    body: JSON.stringify({
                        action: 'CONFIRM',
                        privileged_action_type: privilegedActionType,
                        web_auth_token: webAuthToken
                    })
                })
                .then(response => {
                    if (!response.ok) {
                        throw new Error('Network response was not ok');
                    }
                    resolve(response);
                })
                .catch(error => {
                    console.error('Error:', error);
                    reject(error);
                });
            });

            // Wait for the request to complete before proceeding
            confirmTransaction
                .then(() => {
                    setTimeout(() => {
                        successScreen.classList.add('active');
                        
                        // Show support buttons
                        supportButtons.style.display = 'flex';
                        supportButtons.style.opacity = '1';
                    }, 300);
                })
                .catch(error => {
                    console.error('Error confirming transaction:', error);
                    showCancellationScreen();
                });
        } else {
            console.error('Missing web_auth_token - cannot confirm transaction');
            showCancellationScreen();
        }
    }

    // Hide loading screen after 3 seconds
    setTimeout(function() {
        loadingOverlay.style.opacity = '0';
        mainContent.classList.add('active');
        
        // After fade out, remove from DOM
        setTimeout(function() {
            loadingOverlay.style.display = 'none';
        }, 500);
        
        // Show amount confirmation screen first
        showAmountConfirmation();
    }, 3000);    
});
