exports.handler = (event, context, callback) => {

  // Confirm the user
  event.response.autoConfirmUser = true;

  // Return to Amazon Cognito
  callback(null, event);
};