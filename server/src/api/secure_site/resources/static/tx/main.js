document.addEventListener('DOMContentLoaded', function() {    
    const data = JSON.parse(document.getElementById('verification-params').textContent);
    const {recipient, amountFiat, amountSats, amountCurrency} = data;

    const formattedAmountFiat = formatCurrency(amountFiat, amountCurrency);    
    const roundedAmountFiat = roundFiatAmount(amountFiat, amountCurrency);

    const amountScreen = document.getElementById('amountConfirmation');
    const verificationScreen = document.getElementById('addressVerification');
    const cancellationScreen = document.getElementById('cancellationScreen');
    const successScreen = document.getElementById('successScreen');
    const amountButtons = document.getElementById('amountButtons');
    const verificationButtons = document.getElementById('verificationButtons');   
    const supportButtons = document.getElementById('supportButtons');
    const skipAndVerifyButton = document.getElementById('skipAndVerifyBtn');
    
    // Get all characters from the address
    const allCharacters = recipient.split('');
    
    const segmentSize = 4;
    const numSegments = Math.ceil(allCharacters.length / segmentSize);
    // Skip the first segment, so start from index 1
    const availableSegments = Array.from({length: numSegments - 1}, (_, i) => i + 1);

    // Randomly pick 4 different segments
    const chosenSegments = [];
    while (chosenSegments.length < 4 && availableSegments.length > 0) {
        const idx = Math.floor(Math.random() * availableSegments.length);
        chosenSegments.push(availableSegments.splice(idx, 1)[0]);
    }

    // For each chosen segment, pick a random character index within that segment
    const randomIndices = chosenSegments.map(segment => {
        const start = segment * segmentSize;
        const end = Math.min(start + segmentSize, allCharacters.length);
        return start + Math.floor(Math.random() * (end - start));
    });
    
    // Create array of [character, index] for verification
    const charactersToVerify = randomIndices.map(index => [allCharacters[index], index]);
    
    const addressGrid = document.querySelector('#addressGrid');
    const fullAddressContainer = document.getElementById('fullAddressText');

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
            const satsText = formatSatoshiAmount(amountSats, data.useBip177);
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

    function createCodeRow(elements) {
        const row = document.createElement('div');
        row.classList.add('code-row');
        elements.forEach(el => row.appendChild(el));
        return row;
    }
    
    function buildAddressGrid(addressStr, targets) {
        const chars = addressStr.split('');
        const remainingTargets = [...targets]; // copy for tracking
        let currentRow = [];
        let currentSegment = [];

        chars.forEach((char, charIndex) => {
            // Check if this character position is in our targets
            const targetIndex = remainingTargets.findIndex(([_, idx]) => idx === charIndex);
            
            if (targetIndex !== -1) {
                // Create input
                const input = document.createElement('input');
                input.type = 'text';
                input.maxLength = 1;
                input.className = 'character-input';
                input.dataset.expected = remainingTargets[targetIndex][0].toLowerCase();
                currentSegment.push(input);
    
                // Remove matched target
                remainingTargets.splice(targetIndex, 1);
            } else {
                // Create static span
                const span = document.createElement('span');
                span.className = 'code-text';
                span.textContent = char;
                currentSegment.push(span);
            }
            
            // Every 4 characters becomes a segment
            if (currentSegment.length === 4 || charIndex === chars.length - 1) {
                const segment = document.createElement('div');
                segment.className = 'address-segment';
                currentSegment.forEach(el => segment.appendChild(el));
                currentRow.push(segment);
                currentSegment = [];

                // Add separator after segment if not the last one
                if (charIndex < chars.length - 1) {
                    const sep = document.createElement('span');
                    sep.className = 'separator';
                    sep.textContent = '-';
                    currentRow.push(sep);
                }
            }
            
            // Every 4 segments becomes a row
            if (currentRow.length === 8 || charIndex === chars.length - 1) { // 8 because we now have segments + separators
                const row = createCodeRow(currentRow);
                addressGrid.appendChild(row);
                currentRow = [];
            }
        });
    } 
     
    function buildFullAddressDisplay(addressStr, container) {
        const chars = addressStr.split('');
        let currentSegment = [];
        let currentRow = [];
    
        chars.forEach((char, charIndex) => {
            // Create the character span
            const charSpan = document.createElement('span');
            charSpan.className = 'code-text';
            charSpan.textContent = char;
            currentSegment.push(charSpan);
            
            // Every 4 characters becomes a segment
            if (currentSegment.length === 4 || charIndex === chars.length - 1) {
                const segment = document.createElement('div');
                segment.className = 'address-segment';
                currentSegment.forEach(el => segment.appendChild(el));
                currentRow.push(segment);
                currentSegment = [];

                // Add separator after segment if not the last one
                if (charIndex < chars.length - 1) {
                    const sep = document.createElement('span');
                    sep.className = 'separator';
                    sep.textContent = '-';
                    currentRow.push(sep);
                }
            }
            
            // Every 4 segments becomes a row
            if (currentRow.length === 8 || charIndex === chars.length - 1) { // 8 because we now have segments + separators
                const row = document.createElement('div');
                row.className = 'code-row';
                currentRow.forEach(el => row.appendChild(el));
                container.appendChild(row);
                currentRow = [];
            }
        });
    }

    /**
     * Format currency amount with optional rounding
     * @param {number} amount - The amount to format
     * @param {string} currencyCode - The currency code (e.g., 'USD', 'BTC', 'XXX')
     * @param {Object} options - Formatting options
     * @param {boolean} options.round - Whether to round amounts above 100 to whole numbers
     * @returns {string} Formatted currency string
     */
    function formatCurrency(amount, currencyCode, options = {}) {
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
        verificationScreen.classList.remove('active');
        successScreen.classList.remove('active');
        
        amountButtons.style.display = 'flex';
        amountButtons.style.opacity = '1';
        verificationButtons.style.display = 'none';
        verificationButtons.style.opacity = '0';
        
        // Set up button handlers
        const yesButton = document.getElementById('yesBtn');
        const noButton = document.getElementById('noBtn');
        
        // Remove previous event listeners if any
        const newYesButton = yesButton.cloneNode(true);
        const newNoButton = noButton.cloneNode(true);
        yesButton.parentNode.replaceChild(newYesButton, yesButton);
        noButton.parentNode.replaceChild(newNoButton, noButton);
        
        // Add new event listeners
        newYesButton.addEventListener('click', function() {
            // Transition to verification screen with animation
            amountScreen.classList.remove('active');
            amountButtons.style.opacity = '0';
            
            setTimeout(function() {
                amountButtons.style.display = 'none';
                verificationButtons.style.display = 'flex';
                verificationScreen.classList.add('active');
                
                setTimeout(function() {
                    verificationButtons.style.opacity = '1';
                    
                    // Initialize the address verification screen
                    initializeAddressVerification();
                }, 50);
            }, 300);
        });
        
        newNoButton.addEventListener('click', function() {
            cancelTransaction();
        });
    }
    
    // Function to handle transaction cancellation API call
    function cancelTransaction() {
        if (data.cancelToken) {
            // Create a Promise for the PUT request
            const cancelTransactionPromise = new Promise((resolve, reject) => {
                fetch(`/api/tx-verify/${data.verificationId}`, {
                    method: 'PUT',
                    headers: {
                        'Content-Type': 'application/json'
                    },
                    body: JSON.stringify({
                        action: 'cancel',
                        cancel_token: data.cancelToken
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
        verificationScreen.classList.remove('active');
        amountButtons.style.opacity = '0';
        verificationButtons.style.opacity = '0';
        
        setTimeout(function() {
            amountButtons.style.display = 'none';
            verificationButtons.style.display = 'none';
            supportButtons.style.display = 'flex';
            cancellationScreen.classList.add('active');
            
            setTimeout(function() {
                // Show support buttons
                supportButtons.style.display = 'flex';
                supportButtons.style.opacity = '1';
                supportButtons.querySelector('.secondary-button').classList.remove('invisible');
            }, 50);
        }, 300);
    }
    
    // Main app initialization for address verification
    function initializeAddressVerification() {
        // Get all input fields
        const inputFields = document.querySelectorAll('.character-input');
        const verificationButtons = document.getElementById('verificationButtons');
        const checkButton = document.getElementById('confirmBtn');
        const cancelButton = document.getElementById('cancelBtn');
        
        // Focus the first input after a delay
        setTimeout(() => {
            if (inputFields.length > 0) {
                inputFields[0].focus();
            }
        }, 500);
        
        // Immediately disable the check button
        checkButton.disabled = true;
        
        // Force the disabled state with an attribute as well
        checkButton.setAttribute('disabled', 'disabled');
        
        // Add default styling to inputs
        inputFields.forEach(input => {
            input.style.transition = 'all 0.5s ease';
        });

        function confirmTransaction() {
            // Transition to success screen
            if (data.confirmToken) {
                // Create a Promise for the PUT request
                const confirmTransaction = new Promise((resolve, reject) => {
                    fetch(`/api/tx-verify/${data.verificationId}`, {
                        method: 'PUT',
                        headers: {
                            'Content-Type': 'application/json'
                        },
                        body: JSON.stringify({
                            action: 'confirm',
                            confirm_token: data.confirmToken
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
                        // Fade out verification
                        verificationScreen.classList.remove('active');
                        verificationButtons.style.opacity = '0';
                        
                        setTimeout(() => {
                            verificationButtons.style.display = 'none';
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
            }
        }
        
        // Function to check inputs and update button state
        function checkInputs() {
            let allCorrect = true;
            let allFilled = true;
            
            inputFields.forEach((input) => {
                if (input.value === '') {
                    allFilled = false;
                } else if (input.value.toLowerCase() !== input.dataset.expected) {
                    allCorrect = false;
                }
            });
            
            // Update button state
            if (allFilled && allCorrect) {
                checkButton.disabled = false;
                checkButton.removeAttribute('disabled');
                // Automatically click the button
                checkButton.click();
            } else {
                checkButton.disabled = true;
                checkButton.setAttribute('disabled', 'disabled');
            }
        }    
        
        // Run check on page load
        checkInputs();
        
        // Add input event listeners to each input field
        inputFields.forEach((input, index) => {
            // Focus the first input field on page load
            if (index === 0) {
                setTimeout(() => input.focus(), 500);
            }
            
            // Handle blur event - ensure no outline if empty
            input.addEventListener('blur', function() {
                if (this.value === '') {
                    this.style.outline = 'none';
                    this.style.border = 'none';
                    this.style.backgroundColor = '#3a3d46';
                }
            });
            
            // Input event handler
            input.addEventListener('input', function() {
                const value = this.value.toLowerCase();
                const expected = this.dataset.expected;
                
                if (value === '') {
                    // Reset styling if empty
                    this.setAttribute('style', 'border: none !important; outline: none !important; background-color: #3a3d46 !important;');
                    checkInputs();
                    return;
                }
                
                // Check if input is correct
                if (value === expected) {
                    // Correct input - use setAttribute for maximum override
                    this.setAttribute('style', 'border: 1px solid #00cc00 !important; outline: 1px solid #00cc00 !important; background-color: rgba(0, 204, 0, 0.2) !important;');
                } else {
                    // Incorrect input - use setAttribute for maximum override
                    this.setAttribute('style', 'border: 1px solid #ff0000 !important; outline: 1px solid #ff0000 !important; background-color: rgba(255, 0, 0, 0.2) !important;');
                }
                
                // Move to next input if not the last one
                if (index < inputFields.length - 1) {
                    inputFields[index + 1].focus();
                }
                
                // Check if all inputs are filled and correct
                checkInputs();
            });
            
            // Key navigation
            input.addEventListener('keydown', function(e) {
                switch (e.key) {
                    case 'ArrowRight':
                        if (index < inputFields.length - 1) {
                            inputFields[index + 1].focus();
                        }
                        break;
                    case 'ArrowLeft':
                        if (index > 0) {
                            inputFields[index - 1].focus();
                        }
                        break;
                    case 'Backspace':
                        if (this.value === '' && index > 0) {
                            inputFields[index - 1].focus();
                        }
                        break;
                    case 'Enter':
                        // Find and click the primary button
                        const primaryButton = document.getElementById('confirmBtn');
                        if (primaryButton && !primaryButton.disabled) {
                            primaryButton.click();
                        }
                        break;
                }
            });
        });
        
        // Check button click handler
        checkButton.addEventListener('click', function() {
            // Check if all inputs are correct
            let allCorrect = true;
            
            inputFields.forEach((input, index) => {
                if (input.value.toLowerCase() !== input.dataset.expected) {
                    allCorrect = false;
                }
            });
            
            if (allCorrect) {
                // Confirm the transaction
                confirmTransaction();
            } else {
                // If not all correct, shake the incorrect inputs
                inputFields.forEach((input, index) => {
                    if (input.value.toLowerCase() !== input.dataset.expected) {
                        input.style.animation = 'shake 0.5s';
                        setTimeout(() => {
                            input.style.animation = '';
                        }, 500);
                    }
                });
            }
        });
        
        // "Address doesn't match" button handler
        cancelButton.addEventListener('click', function() {
            cancelTransaction();
        });

        // "Skip" button handler
        skipAndVerifyButton.addEventListener('click', function() {
            confirmTransaction();
        });
    }

    // Run this after DOM is ready
    buildAddressGrid(recipient, charactersToVerify);        
    buildFullAddressDisplay(recipient, fullAddressContainer);    
    
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
