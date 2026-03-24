/**
 * Error Handler Utility
 * Converts technical database/server errors into user-friendly messages
 */

/**
 * Format error message for client response
 * @param {Error} error - The error object
 * @returns {string} - User-friendly error message
 */
function formatErrorMessage(error) {
  const errorMessage = error.message || '';

  // MongoDB E11000 duplicate key errors
  if (error.code === 11000 || errorMessage.includes('E11000')) {
    const field = errorMessage.match(/index: (\w+)/)?.[1] || 'field';
    
    if (field.includes('email')) {
      return 'This email is already registered. Please use a different email or log in.';
    }
    if (field.includes('username')) {
      return 'This username is already taken. Please choose a different username.';
    }
    if (field.includes('firebaseUid')) {
      return 'This account is already registered. Please try logging in.';
    }
    return 'This information is already in use. Please try different values.';
  }

  // Validation errors
  if (error.name === 'ValidationError') {
    return 'Please check your information and try again.';
  }

  // Firebase errors
  if (errorMessage.includes('Firebase') || errorMessage.includes('auth')) {
    if (errorMessage.includes('email-already-in-use')) {
      return 'This email is already in use. Please use a different email.';
    }
    if (errorMessage.includes('weak-password')) {
      return 'Password is too weak. Please use a stronger password.';
    }
    if (errorMessage.includes('user-not-found')) {
      return 'Account not found. Please check your credentials.';
    }
    return 'Authentication failed. Please try again.';
  }

  // Network/connection errors
  if (errorMessage.includes('ECONNREFUSED') || errorMessage.includes('TIMEOUT')) {
    return 'Connection failed. Please check your internet and try again.';
  }

  // Default fallback
  return 'Something went wrong. Please try again later.';
}

/**
 * Safe error handler for Express routes
 * Logs technical details but returns user-friendly message
 * @param {Error} error - The error object
 * @param {Object} res - Express response object
 * @param {number} statusCode - HTTP status code (default 500)
 * @param {string} context - Brief context for logging (e.g., 'Register unverified')
 */
function handleError(error, res, statusCode = 500, context = '') {
  // Log full error for debugging
  if (context) {
    console.error(`❌ ${context}:`, error.message || error);
  } else {
    console.error('❌ Error:', error.message || error);
  }

  // Return user-friendly message
  const userMessage = formatErrorMessage(error);
  
  res.status(statusCode).json({
    success: false,
    message: userMessage,
    error: userMessage
  });
}

module.exports = {
  formatErrorMessage,
  handleError
};
