const axios = require('axios');

// Configuration
const API_BASE = 'http://localhost:5000/api';
const TEST_EMAIL = `test${Date.now()}@example.com`;
const TEST_USERNAME = `user${Date.now()}`.substring(0, 20); // Limit to 20 chars
const TEST_FULLNAME = 'Test User';
const TEST_PHONE = '+1234567890';

let testResults = {
  passed: 0,
  failed: 0,
  results: []
};

// Helper function to make API calls
async function apiCall(method, endpoint, data = null) {
  try {
    const config = {
      method,
      url: `${API_BASE}${endpoint}`,
      headers: { 'Content-Type': 'application/json' },
      validateStatus: () => true // Don't throw on any status
    };
    
    if (data) config.data = data;
    
    const response = await axios(config);
    return {
      status: response.status,
      body: response.data,
      success: response.status >= 200 && response.status < 300
    };
  } catch (error) {
    return {
      status: 0,
      body: null,
      success: false,
      error: error.message
    };
  }
}

// Test 1: Register unverified user
async function testRegisterUnverified() {
  console.log('\n📝 TEST 1: POST /api/auth/register-unverified');
  
  const response = await apiCall('POST', '/auth/register-unverified', {
    email: TEST_EMAIL,
    username: TEST_USERNAME,
    fullName: TEST_FULLNAME,
    phone: TEST_PHONE
  });
  
  const passed = response.status === 201 && response.body.success;
  
  logResult('Register Unverified', passed, response);
  
  return {
    passed,
    data: response.body?.user
  };
}

// Test 2: Attempt duplicate email registration
async function testDuplicateEmail() {
  console.log('\n📝 TEST 2: Duplicate Email Prevention');
  
  const response = await apiCall('POST', '/auth/register-unverified', {
    email: TEST_EMAIL,
    username: 'different_username',
    fullName: 'Another User',
    phone: TEST_PHONE
  });
  
  const passed = response.status === 409; // Conflict
  
  logResult('Duplicate Email Prevention', passed, response);
  
  return passed;
}

// Test 3: Attempt duplicate username registration
async function testDuplicateUsername() {
  console.log('\n📝 TEST 3: Duplicate Username Prevention');
  
  const response = await apiCall('POST', '/auth/register-unverified', {
    email: `another${Date.now()}@example.com`,
    username: TEST_USERNAME,
    fullName: 'Another User',
    phone: TEST_PHONE
  });
  
  const passed = response.status === 409; // Conflict
  
  logResult('Duplicate Username Prevention', passed, response);
  
  return passed;
}

// Test 4: Missing required fields
async function testMissingFields() {
  console.log('\n📝 TEST 4: Missing Required Fields');
  
  const response = await apiCall('POST', '/auth/register-unverified', {
    email: `test${Date.now()}@example.com`,
    username: 'testuser'
    // Missing fullName
  });
  
  const passed = response.status === 400; // Bad request
  
  logResult('Missing Fields Validation', passed, response);
  
  return passed;
}

// Test 5: Check email verified (should be false initially)
async function testCheckEmailNotVerified() {
  console.log('\n📝 TEST 5: Check Email Not Verified');
  
  // First need a Firebase UID - we'll use a dummy one for this test
  const dummyFirebaseUid = 'test_firebase_uid_' + Date.now();
  
  const response = await apiCall('POST', '/auth/check-email-verified', {
    firebaseUid: dummyFirebaseUid
  });
  
  // This should fail with 404 since we don't have a real user
  const passed = response.status === 404;
  
  logResult('Check Email Not Verified', passed, response);
  
  return passed;
}

// Test 6: User model includes verification fields
async function testUserModelFields() {
  console.log('\n📝 TEST 6: User Model Email Verification Fields');
  
  try {
    const User = require('./models/User');
    
    // Check schema paths exist
    const schema = User.schema;
    const hasEmailVerified = schema.paths.emailVerified !== undefined;
    const hasEmailVerificationToken = schema.paths.emailVerificationToken !== undefined;
    
    const passed = hasEmailVerified && hasEmailVerificationToken;
    
    logResult('User Model Fields', passed, {
      emailVerified: hasEmailVerified,
      emailVerificationToken: hasEmailVerificationToken
    });
    
    return passed;
  } catch (error) {
    logResult('User Model Fields', false, { error: error.message });
    return false;
  }
}

// Test 7: Auth routes exist
async function testAuthRoutesExist() {
  console.log('\n📝 TEST 7: Auth Routes Registered');
  
  try {
    const express = require('express');
    const authRoutes = require('./routes/auth');
    
    // Check if routes is a router
    const passed = authRoutes && typeof authRoutes === 'function';
    
    logResult('Auth Routes Exist', passed, {
      isRouter: typeof authRoutes === 'function',
      type: typeof authRoutes
    });
    
    return passed;
  } catch (error) {
    logResult('Auth Routes Exist', false, { error: error.message });
    return false;
  }
}

// Logging helper
function logResult(testName, passed, data) {
  const status = passed ? '✅ PASS' : '❌ FAIL';
  console.log(`${status}: ${testName}`);
  
  if (data?.status) {
    console.log(`  Status: ${data.status}`);
  }
  
  if (data?.body?.message) {
    console.log(`  Message: ${data.body.message}`);
  }
  
  if (data?.body?.error) {
    console.log(`  Error: ${data.body.error}`);
  }
  
  if (!passed && data?.body) {
    console.log(`  Response: ${JSON.stringify(data.body).substring(0, 100)}...`);
  }
  
  testResults.results.push({
    name: testName,
    passed
  });
  
  if (passed) {
    testResults.passed++;
  } else {
    testResults.failed++;
  }
}

// Run all tests
async function runAllTests() {
  console.log('🧪 EMAIL VERIFICATION SYSTEM TESTS');
  console.log('===================================\n');
  console.log(`Test Email: ${TEST_EMAIL}`);
  console.log(`Test Username: ${TEST_USERNAME}\n`);
  
  // Run registration tests
  const regTest = await testRegisterUnverified();
  
  if (regTest.passed) {
    console.log('✅ User created successfully');
  }
  
  // Run validation tests
  await testDuplicateEmail();
  await testDuplicateUsername();
  await testMissingFields();
  
  // Run verification tests
  await testCheckEmailNotVerified();
  
  // Run model tests
  await testUserModelFields();
  
  // Run route tests
  await testAuthRoutesExist();
  
  // Print summary
  console.log('\n===================================');
  console.log('📊 TEST SUMMARY');
  console.log('===================================');
  console.log(`✅ Passed: ${testResults.passed}`);
  console.log(`❌ Failed: ${testResults.failed}`);
  console.log(`📈 Total: ${testResults.passed + testResults.failed}`);
  console.log(`Pass Rate: ${Math.round(testResults.passed / (testResults.passed + testResults.failed) * 100)}%\n`);
  
  if (testResults.failed === 0) {
    console.log('🎉 ALL TESTS PASSED!\n');
  } else {
    console.log(`⚠️  ${testResults.failed} test(s) failed\n`);
  }
  
  // Exit with appropriate code
  process.exit(testResults.failed === 0 ? 0 : 1);
}

// Run tests
runAllTests().catch(error => {
  console.error('❌ Test execution error:', error);
  process.exit(1);
});
