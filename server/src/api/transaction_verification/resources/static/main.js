document.addEventListener('DOMContentLoaded', function() {    
    const data = JSON.parse(document.getElementById('verification-params').textContent);
    const {recipient, amountFiat, amountSats} = data;
    
    // Get all characters from the address
    const allCharacters = recipient.split('');
    
    // Function to get random unique indices
    function getRandomIndices(array, count) {
        const indices = [];
        while (indices.length < count) {
            const randomIndex = Math.floor(Math.random() * array.length);
            if (!indices.includes(randomIndex)) {
                indices.push(randomIndex);
            }
        }
        return indices;
    }
    
    // Get 4 random unique indices
    const randomIndices = getRandomIndices(allCharacters, 4);
    
    // Create array of [character, index] for verification
    const charactersToVerify = randomIndices.map(index => [allCharacters[index], index]);
    
    const addressGrid = document.querySelector('#addressGrid');
    const fullAddressContainer = document.getElementById('fullAddressText');

    document.getElementById('amountFiat').innerText = amountFiat;
    document.getElementById('amountSats').innerText = amountSats;

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

        console.log(targets);
    
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
    
    
    // Function to show the amount confirmation screen
    function showAmountConfirmation() {
        const amountScreen = document.getElementById('amountConfirmation');
        const verificationScreen = document.getElementById('addressVerification');
        const successScreen = document.getElementById('successScreen');
        const amountButtons = document.getElementById('amountButtons');
        const verificationButtons = document.getElementById('verificationButtons');
        
        // Show amount screen, hide others
        amountScreen.classList.add('active');
        verificationScreen.classList.remove('active');
        successScreen.classList.remove('active');
        
        amountButtons.style.display = 'flex';
        amountButtons.style.opacity = '1';
        verificationButtons.style.display = 'none';
        verificationButtons.style.opacity = '0';
        
        // Set up button handlers
        const yesButton = amountButtons.querySelector('.primary-button');
        const noButton = amountButtons.querySelector('.secondary-button');
        
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
            alert('Transaction cancelled.');
        });
    }
    
    // Main app initialization for address verification
    function initializeAddressVerification() {
        console.log('JavaScript is running!');
        
        // Get all input fields
        const inputFields = document.querySelectorAll('.character-input');
        const verificationButtons = document.getElementById('verificationButtons');
        const checkButton = verificationButtons.querySelector('.primary-button');
        const skipButton = verificationButtons.querySelector('.secondary-button');
        
        // Focus the first input after a delay
        setTimeout(() => {
            if (inputFields.length > 0) {
                inputFields[0].focus();
            }
        }, 500);
        
        // Immediately disable the check button
        checkButton.disabled = true;
        console.log('Button disabled on load:', checkButton.disabled);
        
        // Force the disabled state with an attribute as well
        checkButton.setAttribute('disabled', 'disabled');
        
        // Add default styling to inputs
        inputFields.forEach(input => {
            input.style.transition = 'all 0.5s ease';
        });
        
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
                console.log('All correct! Button enabled.');
            } else {
                checkButton.disabled = true;
                checkButton.setAttribute('disabled', 'disabled');
                console.log('Not all correct. Button disabled.');
            }
            
            console.log(`All filled: ${allFilled}, All correct: ${allCorrect}`);
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
                
                console.log(`Input ${index}: Entered "${value}", Expected "${expected}"`);
                
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
                        const primaryButton = document.querySelector('.primary-button:not([disabled])');
                        if (primaryButton) {
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
                if (data.confirmToken) {
                    console.log('Confirming transaction with token: ', data.confirmToken);
                    // Create a Promise for the PUT request
                    const confirmTransaction = new Promise((resolve, reject) => {
                        fetch(`/api/tx-verify/confirm?token=${data.confirmToken}`, {
                            method: 'PUT',
                            headers: {
                                'Content-Type': 'application/json'
                            }
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
                        // TODO: Remove this once the server is ready to handle the request
                        resolve();
                    });

                    // Wait for the request to complete before proceeding
                    confirmTransaction
                        .then(() => {
                            // Transition to success screen
                            const verificationScreen = document.getElementById('addressVerification');
                            const successScreen = document.getElementById('successScreen');
                            const verificationButtons = document.getElementById('verificationButtons');
                            
                            // Fade out verification
                            verificationScreen.classList.remove('active');
                            verificationButtons.style.opacity = '0';
                            
                            setTimeout(() => {
                                verificationButtons.style.display = 'none';
                                successScreen.classList.add('active');
                                
                                // Force original styles first to allow transition to be visible
                                const separators = successScreen.querySelectorAll('.separator');
                                const codeTexts = successScreen.querySelectorAll('.code-text');
                                const codeRows = successScreen.querySelectorAll('.code-row');
                                const addressGrid = successScreen.querySelector('.address-grid');
                                
                                // Then transition all elements to their success state
                                setTimeout(() => {                                    
                                    // Hide buttons
                                    const buttonContainer = document.querySelector('.button-container');
                                    if (buttonContainer) {
                                        buttonContainer.style.transition = 'transform 0.5s ease, opacity 0.5s ease';
                                        buttonContainer.style.transform = 'translateY(100%)';
                                        buttonContainer.style.opacity = '0';
                                    }
                                }, 300);
                            }, 300);
                        })
                        .catch(error => {
                            console.error('Error confirming transaction:', error);
                            alert('An error occurred while confirming the transaction.');
                        });
                }
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
        
        // Skip button handler
        skipButton.addEventListener('click', function() {
            if (data.cancelToken) {
                console.log('Cancelling transaction with token: ', data.cancelToken);
                // Create a Promise for the PUT request
                const cancelTransaction = new Promise((resolve, reject) => {
                    fetch(`/api/tx-verify/cancel?token=${data.cancelToken}`, {
                        method: 'PUT',
                        headers: {
                            'Content-Type': 'application/json'
                        }
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
                cancelTransaction
                    .then(() => {
                        console.log('Transaction cancelled successfully');
                        window.location.href = '/'; // Redirect to home or appropriate page
                    })
                    .catch(error => {
                        console.error('Error cancelling transaction:', error);
                        alert('An error occurred while cancelling the transaction.');
                    });
            }            
            console.log('Skipping this verification step.');
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
