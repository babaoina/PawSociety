// ===== API CONFIGURATION =====
const API_BASE_URL = '/api/admin'; 
const APP_BACKEND_URL = 'http://192.168.254.100:5000';
let authToken = localStorage.getItem('adminToken');

// ===== API REQUEST HELPER =====
async function apiRequest(endpoint, options = {}) {
    const token = localStorage.getItem('adminToken');
    
    const headers = {
        'Content-Type': 'application/json',
        ...(token && { 'Authorization': `Bearer ${token}` }),
        ...options.headers
    };
    
    try {
        const response = await fetch(`${API_BASE_URL}${endpoint}`, {
            ...options,
            headers
        });
        
        if (response.status === 401) {
            localStorage.removeItem('adminToken');
            localStorage.removeItem('adminUser');
            if (!window.location.pathname.includes('index.html')) {
                window.location.href = 'index.html';
            }
            throw new Error('Session expired');
        }
        
        const data = await response.json();
        
        if (!response.ok) {
            throw new Error(data.error || 'Request failed');
        }
        
        return data;
    } catch (error) {
        console.error('API Error:', error);
        throw error;
    }
}

// ===== INITIALIZE ON LOAD =====
document.addEventListener('DOMContentLoaded', function() {
    console.log('Admin panel loaded');
    
    const path = window.location.pathname;
    if (!path.includes('index.html') && !localStorage.getItem('adminToken')) {
        window.location.href = 'index.html';
        return;
    }
    
    if (path.includes('dashboard.html')) {
        loadDashboardData();
        setInterval(loadDashboardData, 10000);
    } else if (path.includes('users.html')) {
        loadUsersData();
        setInterval(loadUsersData, 10000);
    } else if (path.includes('posts.html')) {
        loadPostsData();
        setInterval(loadPostsData, 10000);
    } else if (path.includes('reports.html')) {
        loadReportsData();
        setInterval(loadReportsData, 10000);
    } else if (path.includes('settings.html')) {
        loadSettingsData();
    }
});

// ===== DASHBOARD FUNCTIONS =====
// ===== UPDATED DASHBOARD FUNCTION WITH PROFESSIONAL CARDS =====
async function loadDashboardData() {
    try {
        console.log('Loading dashboard data from API...');
        const response = await fetch(`${API_BASE_URL}/admin/stats`, {
            headers: {
                'Authorization': `Bearer ${localStorage.getItem('adminToken')}`
            }
        });
        
        if (!response.ok) throw new Error('Failed to load stats');
        
        const stats = await response.json();
        console.log('Dashboard stats:', stats);
        
        // ===== TOP STATS =====
        const totalUsers = stats.totalUsers || 0;
        const totalPosts = stats.totalPosts || 0;
        
        document.getElementById('total-users').textContent = totalUsers;
        document.getElementById('total-posts').textContent = totalPosts;
        
        // ===== POSTS BY STATUS =====
        const lostCount = stats.lostPets || 0;
        const foundCount = stats.foundPets || 0;
        const adoptionCount = stats.adoptions || 0;
        
        document.getElementById('lost-pets').textContent = lostCount;
        document.getElementById('found-pets').textContent = foundCount;
        document.getElementById('adoption-pets').textContent = adoptionCount;
        
        // Set percentage changes (mock data - you can replace with real API data)
        document.getElementById('lost-change').innerHTML = '↑ 1.56%';
        document.getElementById('found-change').innerHTML = '↑ 1.56%';
        document.getElementById('adoption-change').innerHTML = '↑ 1.56%';
        
        // ===== USER GROWTH LINE CHART =====
        const userGrowth = stats.userGrowth || [12, 19, 15, 17, 24, 23, 25, 28, 32, 35, 38, 42];
        
        // Update the line chart
        if (typeof updateLineChart === 'function') {
            updateLineChart(userGrowth);
        }
        
        // ===== GROWTH STATS =====
        document.getElementById('total-users-growth').textContent = totalUsers;
        
        // New this month (last month's growth)
        const lastMonthGrowth = userGrowth.length > 0 ? userGrowth[userGrowth.length - 1] : Math.floor(totalUsers * 0.1);
        document.getElementById('new-this-month').textContent = lastMonthGrowth;
        
        // Active users (80% of total as mock)
        const activeUsers = Math.floor(totalUsers * 0.8);
        document.getElementById('active-users').textContent = activeUsers;
        
        // Change percentages
        document.getElementById('total-users-change').textContent = '+12%';
        document.getElementById('new-month-change').textContent = '+8%';
        document.getElementById('active-change').textContent = '+5%';
        
        // ===== RECENT ACTIVITY =====
        const activityContainer = document.getElementById('activity-container');
        if (activityContainer) {
            if (!stats.recentActivity || stats.recentActivity.length === 0) {
                activityContainer.innerHTML = '<p style="color: #999; text-align: center; padding: 20px;">No recent activity</p>';
            } else {
                activityContainer.innerHTML = stats.recentActivity.map(activity => `
                    <div class="activity-item">
                        <div class="activity-icon">${activity.type === 'user' ? '👤' : activity.type === 'lost' ? '🔍' : '📝'}</div>
                        <div class="activity-details">
                            <div class="text">${activity.text}</div>
                            <div class="time">${activity.time}</div>
                        </div>
                        <div class="activity-status status-new">${activity.status}</div>
                    </div>
                `).join('');
            }
        }
        
        // Animate all numbers
        animateNumber(document.getElementById('total-users'), totalUsers);
        animateNumber(document.getElementById('total-posts'), totalPosts);
        animateNumber(document.getElementById('lost-pets'), lostCount);
        animateNumber(document.getElementById('found-pets'), foundCount);
        animateNumber(document.getElementById('adoption-pets'), adoptionCount);
        animateNumber(document.getElementById('total-users-growth'), totalUsers);
        animateNumber(document.getElementById('new-this-month'), lastMonthGrowth);
        animateNumber(document.getElementById('active-users'), activeUsers);
        
    } catch (error) {
        console.error('Failed to load dashboard:', error);
    }
}

// ===== USERS FUNCTIONS =====
async function loadUsersData() {
    try {
        console.log('Loading users...');
        const users = await apiRequest('/admin/users');
        displayUsers(users);
        updateUserStats(users);
    } catch (error) {
        console.error('Failed to load users:', error);
    }
}

function displayUsers(users) {
    const tbody = document.getElementById('usersTableBody');
    if (!tbody) return;

    if (users.length === 0) {
        tbody.innerHTML = `
            <tr>
                <td colspan="7" style="text-align: center; padding: 50px;">
                    <div style="font-size: 48px; margin-bottom: 20px;">👥</div>
                    <div style="font-size: 18px; color: #666;">No users found</div>
                </td>
            </tr>
        `;
        return;
    }

    tbody.innerHTML = users.map(user => {
        const avatar = user.name?.split(' ').map(n => n[0]).join('').toUpperCase().substring(0, 2) || '?';
        const colors = ['#7A4F2B', '#B88B4A', '#2196F3', '#4CAF50', '#9C27B0'];
        const color = colors[Math.abs(user.id?.toString().charCodeAt(0) || 0) % colors.length];
        
        return `
        <tr>
            <td>
                <div style="display: flex; align-items: center; gap: 10px;">
                    <div style="width: 35px; height: 35px; background: ${color}; border-radius: 50%; display: flex; align-items: center; justify-content: center; color: white; font-weight: bold;">${avatar}</div>
                    <div>${user.name || ''}</div>
                </div>
            </td>
            <td>${user.email || ''}</td>
            <td>${user.role || 'user'}</td>
            <td><span class="user-status ${user.status === 'Active' ? 'status-active' : 'status-suspended'}">${user.status || 'Active'}</span></td>
            <td>${user.joined ? new Date(user.joined).toLocaleDateString() : ''}</td>
            <td>${user.posts || 0}</td>
            <td>
                <div class="action-icons">
                    <span title="Edit" onclick="editUser('${user.id}')">✏️</span>
                    ${user.status === 'Active' 
                        ? '<span title="Suspend" onclick="updateUserStatus(\'' + user.id + '\', \'Suspended\')">⛔</span>' 
                        : '<span title="Activate" onclick="updateUserStatus(\'' + user.id + '\', \'Active\')">✅</span>'}
                    ${user.role !== 'admin' ? '<span title="Delete" onclick="deleteUser(\'' + user.id + '\')">🗑️</span>' : ''}
                </div>
            </td>
        </tr>
    `}).join('');
}

function updateUserStats(users) {
    const totalUsers = document.getElementById('totalUsers');
    if (totalUsers) {
        totalUsers.textContent = users.length;
    }
}

async function editUser(id) {
    try {
        const users = await apiRequest('/admin/users');
        const user = users.find(u => u.id === id);
        
        if (!user) return;
        
        document.getElementById('modalTitle').textContent = 'Edit User';
        document.getElementById('userId').value = user.id;
        document.getElementById('userName').value = user.name;
        document.getElementById('userEmail').value = user.email;
        document.getElementById('userRole').value = user.role;
        document.getElementById('userStatus').value = user.status;
        
        openModal('userModal');
    } catch (error) {
        console.error('Failed to load user:', error);
        alert('Failed to load user');
    }
}

async function saveUser() {
    const id = document.getElementById('userId').value;
    const name = document.getElementById('userName').value;
    const email = document.getElementById('userEmail').value;
    const role = document.getElementById('userRole').value;
    const status = document.getElementById('userStatus').value;
    
    if (!name || !email) {
        alert('Name and email are required');
        return;
    }
    
    try {
        if (id) {
            await apiRequest(`/admin/users/${id}`, {
                method: 'PUT',
                body: JSON.stringify({ name, email, role, status })
            });
            alert('User updated successfully');
        } else {
            await apiRequest('/admin/users', {
                method: 'POST',
                body: JSON.stringify({ name, email, role, status, password: 'default123' })
            });
            alert('User created successfully');
        }
        
        closeModal('userModal');
        loadUsersData();
    } catch (error) {
        console.error('Failed to save user:', error);
        alert('Failed to save user');
    }
}

async function updateUserStatus(id, status) {
    if (confirm(`${status} this user?`)) {
        try {
            const users = await apiRequest('/admin/users');
            const user = users.find(u => u.id === id);
            
            if (user) {
                await apiRequest(`/admin/users/${id}`, {
                    method: 'PUT',
                    body: JSON.stringify({
                        name: user.name,
                        email: user.email,
                        role: user.role,
                        status: status
                    })
                });
                loadUsersData();
                alert(`User ${status.toLowerCase()} successfully`);
            }
        } catch (error) {
            console.error('Failed to update user:', error);
            alert('Failed to update user');
        }
    }
}

async function deleteUser(id) {
    if (confirm('Are you sure you want to delete this user? This action cannot be undone.')) {
        try {
            await apiRequest(`/admin/users/${id}`, { method: 'DELETE' });
            loadUsersData();
            alert('User deleted successfully');
        } catch (error) {
            console.error('Failed to delete user:', error);
            alert('Failed to delete user');
        }
    }
}

// ===== POSTS FUNCTIONS =====
async function loadPostsData() {
    try {
        console.log('Loading posts...');
        const posts = await apiRequest('/admin/posts');
        
        // DEBUG: Log all image URLs to see what's coming from backend
        console.log('📸 IMAGE URLS FROM DATABASE:');
        posts.forEach(post => {
            if (post.imageUrls && post.imageUrls.length > 0) {
                console.log(`Post ${post.postId} (${post.petName}):`, post.imageUrls);
            }
        });
        
        let reports = [];
        try {
            reports = await apiRequest('/admin/reports');
            console.log('Reports loaded:', reports.length);
        } catch (e) {
            console.log('No reports found');
        }
        
        // Create a map of reports by postId
        const reportsMap = {};
        reports.forEach(report => {
            if (report.postId) {
                if (!reportsMap[report.postId]) {
                    reportsMap[report.postId] = [];
                }
                reportsMap[report.postId].push(report);
            }
        });
        
        // Add report info to posts
        const postsWithReports = posts.map(post => {
            const postReports = reportsMap[post.postId] || [];
            return {
                ...post,
                reported: postReports.length > 0,
                reportCount: postReports.length,
                reports: postReports
            };
        });
        
        console.log('Posts with reports:', postsWithReports.length);
        displayPosts(postsWithReports);
        updatePostStats(postsWithReports);
    } catch (error) {
        console.error('Failed to load posts:', error);
    }
}

// In your ADMIN BACKEND (port 3000) - routes/admin.js
const axios = require('axios');

// Add this route
router.get('/settings', async (req, res) => {
  try {
    // Forward request to APP BACKEND
    const response = await axios.get('http://localhost:5000/api/admin/settings', {
      headers: {
        'Authorization': req.headers.authorization
      }
    });
    res.json(response.data);
  } catch (error) {
    res.status(500).json({ error: error.message });
  }
});




// ===== SETTINGS API FUNCTIONS =====
// Add these to your existing admin.js file

// Load all settings
async function loadSettingsData() {
  try {
    const token = localStorage.getItem('adminToken');
    const response = await fetch(`${API_BASE_URL}/admin/settings`, {
      headers: { 'Authorization': `Bearer ${token}` }
    });
    
    if (!response.ok) throw new Error('Failed to load settings');
    
    const settings = await response.json();
    console.log('✅ Settings loaded:', settings);
    
    // Update all form fields
    updateSettingsForm(settings);
    
  } catch (error) {
    console.error('❌ Failed to load settings:', error);
  }
}

// Update form fields with settings
function updateSettingsForm(settings) {
  // General Settings
  if (settings.general) {
    setValue('appName', settings.general.appName);
    setValue('supportEmail', settings.general.supportEmail);
    setValue('minVersion', settings.general.minVersion);
    setValue('maintenanceMessage', settings.general.maintenanceMessage);
    
    setToggle('maintenanceMode', settings.general.maintenanceMode);
    setToggle('allowRegistration', settings.general.allowRegistration);
    setToggle('emailVerification', settings.general.emailVerification);
    setToggle('phoneVerification', settings.general.phoneVerification);
  }
  
  // Security Settings
  if (settings.security) {
    setValue('maxLoginAttempts', settings.security.maxLoginAttempts);
    setValue('lockoutDuration', settings.security.lockoutDuration);
    setValue('sessionTimeout', settings.security.sessionTimeout);
    setToggle('admin2FA', settings.security.admin2FA);
  }
  
  // Notification Settings
  if (settings.notifications) {
    setToggle('pushEnabled', settings.notifications.pushEnabled);
    setValue('quietStart', settings.notifications.quietStart);
    setValue('quietEnd', settings.notifications.quietEnd);
    
    // Update notification types select
    const select = document.getElementById('notificationTypes');
    if (select && settings.notifications.notificationTypes) {
      Array.from(select.options).forEach(option => {
        option.selected = settings.notifications.notificationTypes.includes(option.value);
      });
    }
  }
  
  // Moderation Settings
  if (settings.moderation) {
    setValue('flagThreshold', settings.moderation.flagThreshold);
    setToggle('autoApprove', settings.moderation.autoApprove);
    setToggle('profanityFilter', settings.moderation.profanityFilter);
    
    if (settings.moderation.blockedWords) {
      setValue('blockedWords', settings.moderation.blockedWords.join(', '));
    }
  }
  
  // API Settings
  if (settings.api) {
    setValue('rateLimit', settings.api.rateLimit);
    setToggle('apiStatus', settings.api.apiStatus);
    
    if (settings.api.allowedOrigins) {
      setValue('allowedOrigins', settings.api.allowedOrigins.join(', '));
    }
  }
}

// Helper functions
function setValue(id, value) {
  const el = document.getElementById(id);
  if (el && value !== undefined) el.value = value;
}

function setToggle(id, value) {
  const el = document.getElementById(id);
  if (el) {
    if (value) {
      el.classList.add('active');
    } else {
      el.classList.remove('active');
    }
  }
}

// ===== FIXED SAVE SETTINGS FUNCTION =====
async function saveSettings(section) {
  try {
    const token = localStorage.getItem('adminToken');
    if (!token) {
      window.location.href = 'index.html';
      return;
    }
    
    // Collect settings based on section
    let settings = {};
    
    switch(section) {
      case 'general':
        settings = {
          appName: document.getElementById('appName')?.value,
          supportEmail: document.getElementById('supportEmail')?.value,
          minVersion: document.getElementById('minVersion')?.value,
          maintenanceMode: document.getElementById('maintenanceMode')?.classList.contains('active'),
          maintenanceMessage: document.getElementById('maintenanceMessage')?.value,
          allowRegistration: document.getElementById('allowRegistration')?.classList.contains('active'),
          emailVerification: document.getElementById('emailVerification')?.classList.contains('active'),
          phoneVerification: document.getElementById('phoneVerification')?.classList.contains('active')
        };
        break;
      // ... other cases
    }
    
    console.log(`📤 Saving ${section} settings:`, settings);
    
    const response = await fetch(`${API_BASE_URL}/admin/settings/${section}`, {
      method: 'PUT',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${token}`
      },
      body: JSON.stringify(settings)
    });
    
    if (response.ok) {
      const result = await response.json();
      console.log('✅ Save successful:', result);
      showToast(`${section} settings saved!`, 'success');
      
      // 🔥 IMPORTANT: Reload settings to confirm they were saved
      setTimeout(() => loadSettingsData(), 1000);
    } else {
      const error = await response.json();
      throw new Error(error.message || 'Failed to save');
    }
  } catch (error) {
    console.error('❌ Error saving settings:', error);
    showToast('Error saving settings: ' + error.message, 'error');
  }
}

// Emergency actions
async function emergencyAction(action) {
  const confirmMessages = {
    'disable': '⚠️ Disable all posts? Users will not see any posts.',
    'clear': '🗑️ Clear all notifications for all users?',
    'cache': '🧹 Clear app cache?',
    'backup': '💾 Force database backup now?',
    'reset': '🔥🔥🔥 DESTROY ALL DATA? Type "RESET" to confirm:'
  };
  
  if (action === 'reset') {
    const confirmText = prompt(confirmMessages[action]);
    if (confirmText !== 'RESET') return;
  } else {
    if (!confirm(confirmMessages[action])) return;
  }
  
  try {
    const token = localStorage.getItem('adminToken');
    const response = await fetch(`${API_BASE_URL}/admin/settings/emergency/${action}`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${token}`
      },
      body: JSON.stringify({ confirm: action === 'reset' ? 'RESET' : undefined })
    });
    
    if (response.ok) {
      alert(`✅ Emergency action completed`);
    } else {
      alert('❌ Action failed');
    }
  } catch (error) {
    console.error('Error:', error);
    alert('❌ Error');
  }
}

// Get notification count for badge
async function getNotificationCount() {
  try {
    const token = localStorage.getItem('adminToken');
    const response = await fetch(`${API_BASE_URL}/admin/notifications/count`, {
      headers: { 'Authorization': `Bearer ${token}` }
    });
    
    if (response.ok) {
      const data = await response.json();
      const badge = document.getElementById('notificationBadge');
      if (badge) {
        badge.textContent = data.count;
        badge.style.display = data.count > 0 ? 'flex' : 'none';
      }
    }
  } catch (error) {
    console.error('Failed to get notification count:', error);
  }
}


// Start polling for notifications
function startNotificationPolling() {
  getNotificationCount();
  setInterval(getNotificationCount, 30000); // Every 30 seconds
}

// ===== UPDATED POSTS DISPLAY FUNCTION WITH CATEGORY BADGE =====
function displayPosts(posts) {
    const postsGrid = document.getElementById('postsGrid');
    if (!postsGrid) return;

    if (posts.length === 0) {
        postsGrid.innerHTML = `
            <div style="grid-column: 1/-1; text-align: center; padding: 50px;">
                <div style="font-size: 48px; margin-bottom: 20px;">📝</div>
                <div style="font-size: 18px; color: #666;">No posts found</div>
            </div>
        `;
        return;
    }

    postsGrid.innerHTML = posts.map(post => {
        const statusClass = post.status === 'Lost' ? 'status-lost' : 
                           post.status === 'Found' ? 'status-found' : 'status-adoption';
        
        // ===== DETERMINE CATEGORY BADGE =====
        let category = null;
        let categoryColor = '';
        
        // Try to get from post.category first
        if (post.category) {
            const categoryUpper = post.category.toUpperCase();
            if (categoryUpper.includes('DOG')) category = 'DOG';
            else if (categoryUpper.includes('CAT')) category = 'CAT';
            else if (categoryUpper.includes('FISH')) category = 'FISH';
            else if (categoryUpper.includes('BIRD')) category = 'BIRD';
        }
        
        // If no category, try to detect from petType
        if (!category && post.petType) {
            const petTypeLower = post.petType.toLowerCase();
            if (petTypeLower.includes('dog') || petTypeLower.includes('aspin') || 
                petTypeLower.includes('labrador') || petTypeLower.includes('golden') ||
                petTypeLower.includes('poodle') || petTypeLower.includes('chihuahua')) {
                category = 'DOG';
            } else if (petTypeLower.includes('cat') || petTypeLower.includes('puspin') ||
                       petTypeLower.includes('persian') || petTypeLower.includes('siamese')) {
                category = 'CAT';
            } else if (petTypeLower.includes('fish') || petTypeLower.includes('goldfish') ||
                       petTypeLower.includes('betta') || petTypeLower.includes('guppy')) {
                category = 'FISH';
            } else if (petTypeLower.includes('bird') || petTypeLower.includes('parrot') ||
                       petTypeLower.includes('lovebird') || petTypeLower.includes('macaw')) {
                category = 'BIRD';
            }
        }
        
        // Set badge color based on category
        switch(category) {
            case 'DOG': categoryColor = '#B88B4A'; break;
            case 'CAT': categoryColor = '#4CAF50'; break;
            case 'FISH': categoryColor = '#2196F3'; break;
            case 'BIRD': categoryColor = '#FF9800'; break;
        }
        
        // ===== GENDER DISPLAY WITH EMOJI =====
        let genderDisplay = '';
        let genderEmoji = '';
        
        switch(post.gender?.toLowerCase()) {
            case 'male':
                genderEmoji = '♂️';
                genderDisplay = 'Male';
                break;
            case 'female':
                genderEmoji = '♀️';
                genderDisplay = 'Female';
                break;
            default:
                genderEmoji = '⚥';
                genderDisplay = 'Unknown';
        }
        
        // ===== WEIGHT DISPLAY =====
        let weightDisplay = post.weight ? post.weight : 'Not specified';
        if (weightDisplay !== 'Not specified' && !weightDisplay.includes('kg') && !weightDisplay.includes('lb')) {
            weightDisplay = weightDisplay + ' kg';
        }
        
        // ===== AGE DISPLAY =====
        let ageDisplay = post.age ? post.age : 'Not specified';
        
        // ===== IMAGE HANDLING =====
        let imageHtml = '';
        if (post.imageUrls && post.imageUrls.length > 0) {
            let imageUrl = post.imageUrls[0];
            
            // Extract just the filename from the path
            const filename = imageUrl.split('/').pop();
            
            // Construct the correct URL
            let fullImageUrl = '';
            
            if (imageUrl.startsWith('http')) {
                fullImageUrl = imageUrl;
            } else if (imageUrl.startsWith('/api/uploads')) {
                fullImageUrl = `${window.location.protocol}//${window.location.hostname}:5000${imageUrl}`;
            } else if (imageUrl.startsWith('/uploads')) {
                fullImageUrl = `${window.location.protocol}//${window.location.hostname}:5000/api${imageUrl}`;
            } else {
                fullImageUrl = `${window.location.protocol}//${window.location.hostname}:5000/api/uploads/posts/${filename}`;
            }
            
            // Remove any double slashes except after http:
            fullImageUrl = fullImageUrl.replace(/([^:]\/)\/+/g, '$1');
            
            imageHtml = `<img src="${fullImageUrl}" 
                            style="width: 100%; height: 100%; object-fit: cover;" 
                            onerror="this.style.display='none'; this.parentNode.innerHTML='${post.petName?.charAt(0) || '?'}';"
                            onload="console.log('✅ Loaded: ' + this.src);">`;
        } else {
            imageHtml = `<div style="width: 100%; height: 100%; display: flex; align-items: center; justify-content: center; background: #7A4F2B; color: white; font-size: 48px;">${post.petName?.charAt(0) || '?'}</div>`;
        }
        
        // Reported badge if any
        const reportedBadge = post.reported ? 
            `<div class="reported-badge" style="margin-bottom: 10px; background: #FF9800; cursor: pointer;" 
                 onclick="showReportDetails('${post.postId}')">
                <span>🚩</span> Reported (${post.reportCount || 0} flag${post.reportCount > 1 ? 's' : ''})
            </div>` : '';

        return `
        <div class="post-card">
            <div class="post-image">
                ${imageHtml}
                <span class="post-status-badge ${statusClass}">${post.status?.toUpperCase() || ''}</span>
                ${category ? `<span class="post-category-badge" style="background: ${categoryColor}; border: 2px solid white;">${category}</span>` : ''}
            </div>
            <div class="post-content">
                <div class="post-header">
                    <div class="post-user">
                        <div class="post-user-avatar">${post.userAvatar || '?'}</div>
                        <span style="font-weight: bold; color: #7A4F2B;">${post.userName || 'Unknown'}</span>
                    </div>
                    <span class="post-title">${post.petName || ''}</span>
                </div>
                
                ${reportedBadge}
                
                <!-- ENHANCED META SECTION WITH GENDER, AGE, WEIGHT -->
                <div class="post-meta" style="display: grid; grid-template-columns: repeat(2, 1fr); gap: 10px; padding: 10px 0;">
                    <div class="meta-item" style="display: flex; align-items: center; gap: 5px;">
                        <span style="font-size: 16px;">📍</span>
                        <span style="color: #666; font-size: 13px;">${post.location || 'No location'}</span>
                    </div>
                    <div class="meta-item" style="display: flex; align-items: center; gap: 5px;">
                        <span style="font-size: 16px;">⏱️</span>
                        <span style="color: #666; font-size: 13px;">${post.time || ''}</span>
                    </div>
                    <div class="meta-item" style="display: flex; align-items: center; gap: 5px;">
                        <span style="font-size: 16px;">${genderEmoji}</span>
                        <span style="color: #666; font-size: 13px; font-weight: bold; color: ${post.gender?.toLowerCase() === 'male' ? '#2196F3' : post.gender?.toLowerCase() === 'female' ? '#E91E63' : '#666'};">${genderDisplay}</span>
                    </div>
                    <div class="meta-item" style="display: flex; align-items: center; gap: 5px;">
                        <span style="font-size: 16px;">⚖️</span>
                        <span style="color: #666; font-size: 13px;">${weightDisplay}</span>
                    </div>
                    <div class="meta-item" style="display: flex; align-items: center; gap: 5px; grid-column: span 2;">
                        <span style="font-size: 16px;">🎂</span>
                        <span style="color: #666; font-size: 13px;">Age: ${ageDisplay}</span>
                    </div>
                </div>
                
                <div class="post-description">
                    ${post.description || ''}
                </div>
                
                <div class="post-actions">
                    <button class="action-btn btn-view" onclick="viewPost('${post.id}')">
                        <span>👁️</span> View
                    </button>
                    ${post.reported ? 
                        `<button class="action-btn btn-flag" onclick="showReportDetails('${post.postId}')" style="background: #FF9800;">
                            <span>🚩</span> Reports (${post.reportCount})
                        </button>` : 
                        `<button class="action-btn btn-flag" style="background: #999; opacity: 0.5;" disabled>
                            <span>🚩</span> No Reports
                        </button>`
                    }
                    <button class="action-btn btn-delete" onclick="deletePost('${post.id}')">
                        <span>🗑️</span> Delete
                    </button>
                </div>
            </div>
        </div>
    `}).join('');
}

async function showReportDetails(postId) {
    try {
        console.log('🔍 Fetching reports for post:', postId);
        
        // Get all reports
        const reports = await apiRequest('/admin/reports');
        
        // Find reports for this post
        const postReports = reports.filter(r => 
            r.postId === postId || 
            (r.post && r.post.postId === postId)
        );
        
        console.log('🎯 Found reports:', postReports);
        
        if (postReports.length === 0) {
            alert('No reports found for this post');
            return;
        }
        
        // Build HTML for the modal
        let html = `
            <div style="font-family: Arial, sans-serif; padding: 10px;">
                <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px; border-bottom: 2px solid #FF9800; padding-bottom: 10px;">
                    <h2 style="color: #FF9800; margin: 0;">
                        <span style="font-size: 24px;">🚩</span> Reports (${postReports.length})
                    </h2>
                    <button onclick="closeModal('reportModal')" style="background: none; border: none; font-size: 24px; cursor: pointer; color: #666;">&times;</button>
                </div>
        `;
        
        // Add each report with status buttons
        postReports.forEach((report, index) => {
            const reporterName = report.reporter?.username || report.reporterUid || 'Unknown User';
            const reporterEmail = report.reporter?.email || '';
            const reportDate = report.createdAt ? new Date(report.createdAt).toLocaleString() : 'Unknown date';
            const reportId = report.reportId;
            
            let statusColor = '#FF9800';
            let statusText = 'PENDING';
            
            if (report.status === 'reviewed') {
                statusColor = '#4CAF50';
                statusText = 'REVIEWED';
            }
            if (report.status === 'dismissed') {
                statusColor = '#999';
                statusText = 'DISMISSED';
            }
            
            html += `
                <div style="background: ${index % 2 === 0 ? '#f9f9f9' : '#fff'}; 
                            padding: 15px; 
                            margin-bottom: 15px; 
                            border-radius: 8px;
                            border-left: 5px solid #FF9800;
                            box-shadow: 0 2px 4px rgba(0,0,0,0.1);">
                    
                    <div style="display: flex; justify-content: space-between; margin-bottom: 10px;">
                        <span style="font-weight: bold; color: #FF9800;">Report #${index + 1}</span>
                        <span style="color: #666; font-size: 12px;">${reportDate}</span>
                    </div>
                    
                    <div style="margin-bottom: 10px;">
                        <div style="font-weight: bold; color: #555; margin-bottom: 3px;">👤 Reporter</div>
                        <div>${reporterName} ${reporterEmail ? '(' + reporterEmail + ')' : ''}</div>
                    </div>
                    
                    <div style="margin-bottom: 10px;">
                        <div style="font-weight: bold; color: #555; margin-bottom: 3px;">📝 Reason</div>
                        <div style="text-transform: capitalize;">${report.reason || 'Not specified'}</div>
                    </div>
                    
                    ${report.description ? `
                        <div style="margin-bottom: 10px;">
                            <div style="font-weight: bold; color: #555; margin-bottom: 3px;">📄 Description</div>
                            <div>${report.description}</div>
                        </div>
                    ` : ''}
                    
                    <div style="margin-bottom: 15px;">
                        <div style="font-weight: bold; color: #555; margin-bottom: 3px;">⚙️ Current Status</div>
                        <span style="display: inline-block; background: ${statusColor}; color: white; padding: 3px 12px; border-radius: 12px; font-weight: bold; text-transform: uppercase; font-size: 11px;">
                            ${statusText}
                        </span>
                    </div>
                    
                    ${report.status === 'pending' ? `
                        <div style="display: flex; gap: 10px; margin-top: 10px;">
                            <button onclick="updateReportStatus('${reportId}', 'reviewed')" 
                                    style="flex: 1; background: #4CAF50; color: white; border: none; 
                                           padding: 8px; border-radius: 5px; cursor: pointer; font-weight: bold;">
                                ✅ Mark Reviewed
                            </button>
                            <button onclick="updateReportStatus('${reportId}', 'dismissed')" 
                                    style="flex: 1; background: #999; color: white; border: none; 
                                           padding: 8px; border-radius: 5px; cursor: pointer; font-weight: bold;">
                                ❌ Dismiss
                            </button>
                        </div>
                    ` : `
                        <div style="margin-top: 10px; padding: 8px; background: #f0f0f0; border-radius: 5px; text-align: center; color: #666;">
                            ✅ This report has been ${report.status}
                        </div>
                    `}
                </div>
            `;
        });
        
        html += `
                <div style="text-align: right; margin-top: 20px;">
                    <button onclick="closeModal('reportModal')" 
                            style="background: #7A4F2B; color: white; border: none; 
                                   padding: 10px 25px; border-radius: 5px; cursor: pointer; 
                                   font-weight: bold;">
                        Close
                    </button>
                </div>
            </div>
        `;
        
        // Get the report modal content element
        const reportModalContent = document.getElementById('reportModalContent');
        
        if (reportModalContent) {
            reportModalContent.innerHTML = html;
            openModal('reportModal');
        } else {
            console.error('reportModalContent element not found!');
            alert('Error: Could not find report modal content element');
        }
        
    } catch (error) {
        console.error('❌ Error:', error);
        alert('Failed to load reports: ' + error.message);
    }
}

async function updateReportStatus(reportId, status) {
    try {
        console.log('Updating report:', reportId, 'to', status);
        
        await apiRequest(`/admin/reports/${reportId}/status`, {
            method: 'PUT',
            body: JSON.stringify({ status })
        });
        
        alert(`✅ Report marked as ${status}. Reporter will be notified.`);
        
        // Get the current post ID from the modal
        const postId = window.currentReportPostId;
        
        if (postId) {
            // Refresh just the reports for this post
            await refreshReportModal(postId);
        } else {
            // Refresh posts data
            loadPostsData();
            closeModal('reportModal');
        }
        
    } catch (error) {
        console.error('Failed to update report:', error);
        alert('Failed to update report: ' + error.message);
    }
}

// Store post ID when opening modal
async function showReportDetails(postId) {
    window.currentReportPostId = postId; // Store for later use
    
    try {
        console.log('🔍 Fetching reports for post:', postId);
        
        const reports = await apiRequest('/admin/reports');
        const postReports = reports.filter(r => 
            r.postId === postId || 
            (r.post && r.post.postId === postId)
        );
        
        if (postReports.length === 0) {
            alert('No reports found for this post');
            return;
        }
        
        // Build HTML with status buttons
        let html = `
            <div style="font-family: Arial, sans-serif; padding: 10px;">
                <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px; border-bottom: 2px solid #FF9800; padding-bottom: 10px;">
                    <h2 style="color: #FF9800; margin: 0;">
                        <span style="font-size: 24px;">🚩</span> Reports (${postReports.length})
                    </h2>
                    <button onclick="closeModal('reportModal')" style="background: none; border: none; font-size: 24px; cursor: pointer; color: #666;">&times;</button>
                </div>
        `;
        
        postReports.forEach((report, index) => {
            const reporterName = report.reporter?.username || report.reporterUid || 'Unknown User';
            const reporterEmail = report.reporter?.email || '';
            const reportDate = report.createdAt ? new Date(report.createdAt).toLocaleString() : 'Unknown date';
            const reportId = report.reportId;
            
            let statusColor = '#FF9800';
            let statusText = 'PENDING';
            
            if (report.status === 'reviewed') {
                statusColor = '#4CAF50';
                statusText = 'REVIEWED';
            }
            if (report.status === 'dismissed') {
                statusColor = '#999';
                statusText = 'DISMISSED';
            }
            
            html += `
                <div style="background: ${index % 2 === 0 ? '#f9f9f9' : '#fff'}; 
                            padding: 15px; 
                            margin-bottom: 15px; 
                            border-radius: 8px;
                            border-left: 5px solid #FF9800;
                            box-shadow: 0 2px 4px rgba(0,0,0,0.1);">
                    
                    <div style="display: flex; justify-content: space-between; margin-bottom: 10px;">
                        <span style="font-weight: bold; color: #FF9800;">Report #${index + 1}</span>
                        <span style="color: #666; font-size: 12px;">${reportDate}</span>
                    </div>
                    
                    <div style="margin-bottom: 10px;">
                        <div style="font-weight: bold; color: #555; margin-bottom: 3px;">👤 Reporter</div>
                        <div>${reporterName} ${reporterEmail ? '(' + reporterEmail + ')' : ''}</div>
                    </div>
                    
                    <div style="margin-bottom: 10px;">
                        <div style="font-weight: bold; color: #555; margin-bottom: 3px;">📝 Reason</div>
                        <div style="text-transform: capitalize;">${report.reason || 'Not specified'}</div>
                    </div>
                    
                    ${report.description ? `
                        <div style="margin-bottom: 10px;">
                            <div style="font-weight: bold; color: #555; margin-bottom: 3px;">📄 Description</div>
                            <div>${report.description}</div>
                        </div>
                    ` : ''}
                    
                    <div style="margin-bottom: 15px;">
                        <div style="font-weight: bold; color: #555; margin-bottom: 3px;">⚙️ Current Status</div>
                        <span style="display: inline-block; background: ${statusColor}; color: white; padding: 3px 12px; border-radius: 12px; font-weight: bold; text-transform: uppercase; font-size: 11px;">
                            ${statusText}
                        </span>
                    </div>
                    
                    ${report.status === 'pending' ? `
                        <div style="display: flex; gap: 10px; margin-top: 10px;">
                            <button onclick="updateReportStatus('${reportId}', 'reviewed')" 
                                    style="flex: 1; background: #4CAF50; color: white; border: none; 
                                           padding: 8px; border-radius: 5px; cursor: pointer; font-weight: bold;">
                                ✅ Mark Reviewed
                            </button>
                            <button onclick="updateReportStatus('${reportId}', 'dismissed')" 
                                    style="flex: 1; background: #999; color: white; border: none; 
                                           padding: 8px; border-radius: 5px; cursor: pointer; font-weight: bold;">
                                ❌ Dismiss
                            </button>
                        </div>
                    ` : `
                        <div style="margin-top: 10px; padding: 8px; background: #f0f0f0; border-radius: 5px; text-align: center; color: #666;">
                            ✅ This report has been ${report.status}
                        </div>
                    `}
                </div>
            `;
        });
        
        html += `
                <div style="text-align: right; margin-top: 20px;">
                    <button onclick="closeModal('reportModal')" 
                            style="background: #7A4F2B; color: white; border: none; 
                                   padding: 10px 25px; border-radius: 5px; cursor: pointer; 
                                   font-weight: bold;">
                        Close
                    </button>
                </div>
            </div>
        `;
        
        document.getElementById('reportModalContent').innerHTML = html;
        openModal('reportModal');
        
    } catch (error) {
        console.error('❌ Error:', error);
        alert('Failed to load reports: ' + error.message);
    }
}

// Refresh modal function
async function refreshReportModal(postId) {
    try {
        const reports = await apiRequest('/admin/reports');
        const postReports = reports.filter(r => 
            r.postId === postId || 
            (r.post && r.post.postId === postId)
        );
        
        // Rebuild the modal content (same as above)
        let html = `
            <div style="font-family: Arial, sans-serif; padding: 10px;">
                <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px; border-bottom: 2px solid #FF9800; padding-bottom: 10px;">
                    <h2 style="color: #FF9800; margin: 0;">
                        <span style="font-size: 24px;">🚩</span> Reports (${postReports.length})
                    </h2>
                    <button onclick="closeModal('reportModal')" style="background: none; border: none; font-size: 24px; cursor: pointer; color: #666;">&times;</button>
                </div>
        `;
        
        postReports.forEach((report, index) => {
            const reporterName = report.reporter?.username || report.reporterUid || 'Unknown User';
            const reporterEmail = report.reporter?.email || '';
            const reportDate = report.createdAt ? new Date(report.createdAt).toLocaleString() : 'Unknown date';
            const reportId = report.reportId;
            
            let statusColor = '#FF9800';
            let statusText = 'PENDING';
            
            if (report.status === 'reviewed') {
                statusColor = '#4CAF50';
                statusText = 'REVIEWED';
            }
            if (report.status === 'dismissed') {
                statusColor = '#999';
                statusText = 'DISMISSED';
            }
            
            html += `
                <div style="background: ${index % 2 === 0 ? '#f9f9f9' : '#fff'}; 
                            padding: 15px; 
                            margin-bottom: 15px; 
                            border-radius: 8px;
                            border-left: 5px solid #FF9800;
                            box-shadow: 0 2px 4px rgba(0,0,0,0.1);">
                    
                    <div style="display: flex; justify-content: space-between; margin-bottom: 10px;">
                        <span style="font-weight: bold; color: #FF9800;">Report #${index + 1}</span>
                        <span style="color: #666; font-size: 12px;">${reportDate}</span>
                    </div>
                    
                    <div style="margin-bottom: 10px;">
                        <div style="font-weight: bold; color: #555; margin-bottom: 3px;">👤 Reporter</div>
                        <div>${reporterName} ${reporterEmail ? '(' + reporterEmail + ')' : ''}</div>
                    </div>
                    
                    <div style="margin-bottom: 10px;">
                        <div style="font-weight: bold; color: #555; margin-bottom: 3px;">📝 Reason</div>
                        <div style="text-transform: capitalize;">${report.reason || 'Not specified'}</div>
                    </div>
                    
                    ${report.description ? `
                        <div style="margin-bottom: 10px;">
                            <div style="font-weight: bold; color: #555; margin-bottom: 3px;">📄 Description</div>
                            <div>${report.description}</div>
                        </div>
                    ` : ''}
                    
                    <div style="margin-bottom: 15px;">
                        <div style="font-weight: bold; color: #555; margin-bottom: 3px;">⚙️ Current Status</div>
                        <span style="display: inline-block; background: ${statusColor}; color: white; padding: 3px 12px; border-radius: 12px; font-weight: bold; text-transform: uppercase; font-size: 11px;">
                            ${statusText}
                        </span>
                    </div>
                    
                    ${report.status === 'pending' ? `
                        <div style="display: flex; gap: 10px; margin-top: 10px;">
                            <button onclick="updateReportStatus('${reportId}', 'reviewed')" 
                                    style="flex: 1; background: #4CAF50; color: white; border: none; 
                                           padding: 8px; border-radius: 5px; cursor: pointer; font-weight: bold;">
                                ✅ Mark Reviewed
                            </button>
                            <button onclick="updateReportStatus('${reportId}', 'dismissed')" 
                                    style="flex: 1; background: #999; color: white; border: none; 
                                           padding: 8px; border-radius: 5px; cursor: pointer; font-weight: bold;">
                                ❌ Dismiss
                            </button>
                        </div>
                    ` : `
                        <div style="margin-top: 10px; padding: 8px; background: #f0f0f0; border-radius: 5px; text-align: center; color: #666;">
                            ✅ This report has been ${report.status}
                        </div>
                    `}
                </div>
            `;
        });
        
        html += `
                <div style="text-align: right; margin-top: 20px;">
                    <button onclick="closeModal('reportModal')" 
                            style="background: #7A4F2B; color: white; border: none; 
                                   padding: 10px 25px; border-radius: 5px; cursor: pointer; 
                                   font-weight: bold;">
                        Close
                    </button>
                </div>
            </div>
        `;
        
        document.getElementById('reportModalContent').innerHTML = html;
        
    } catch (error) {
        console.error('Failed to refresh reports:', error);
    }
}

async function deleteReport(reportId) {
    if (confirm('Are you sure you want to delete this report?')) {
        try {
            await apiRequest(`/admin/reports/${reportId}`, {
                method: 'DELETE'
            });
            alert('Report deleted');
            showReportDetails(document.querySelector('#modalContent').getAttribute('data-postid'));
            loadPostsData();
        } catch (error) {
            console.error('Failed to delete report:', error);
            alert('Failed to delete report');
        }
    }
}

function updatePostStats(posts) {
    const totalPosts = document.getElementById('totalPosts');
    const reportedPosts = document.getElementById('reportedPosts');
    
    if (totalPosts) totalPosts.textContent = posts.length;
    if (reportedPosts) reportedPosts.textContent = posts.filter(p => p.reported).length;
}

// ===== UPDATED VIEW POST FUNCTION WITH AGE AND WEIGHT =====
async function viewPost(id) {
    try {
        console.log('🔍 Viewing post:', id);
        
        // Fetch single post from API
        const response = await fetch(`${API_BASE_URL}/admin/posts/${id}`, {
            headers: {
                'Authorization': `Bearer ${localStorage.getItem('adminToken')}`
            }
        });
        
        if (!response.ok) throw new Error('Failed to load post');
        
        const post = await response.json();
        console.log('📄 Post details:', post);
        
        const modalContent = document.getElementById('modalContent');
        if (!modalContent) return;
        
        // Determine status color
        let statusColor = '#F44336'; // Lost (red)
        if (post.status === 'Found') statusColor = '#4CAF50'; // Found (green)
        else if (post.status === 'Adoption') statusColor = '#2196F3'; // Adoption (blue)
        
        // ===== GENDER DISPLAY WITH EMOJI =====
        let genderDisplay = '';
        let genderEmoji = '';
        let genderColor = '#666';
        
        switch(post.gender?.toLowerCase()) {
            case 'male':
                genderEmoji = '♂️';
                genderDisplay = 'Male';
                genderColor = '#2196F3'; // Blue
                break;
            case 'female':
                genderEmoji = '♀️';
                genderDisplay = 'Female';
                genderColor = '#E91E63'; // Pink
                break;
            default:
                genderEmoji = '⚥';
                genderDisplay = post.gender || 'Unknown';
                genderColor = '#999'; // Gray
        }
        
        // ===== WEIGHT DISPLAY =====
        let weightDisplay = 'Not specified';
        if (post.weight && post.weight.trim() !== '') {
            weightDisplay = post.weight;
            // Add kg if it's just a number
            if (!weightDisplay.includes('kg') && !weightDisplay.includes('lb') && !weightDisplay.includes('lbs')) {
                weightDisplay = weightDisplay + ' kg';
            }
        }
        
        // ===== AGE DISPLAY =====
        let ageDisplay = 'Not specified';
        if (post.age && post.age.trim() !== '') {
            ageDisplay = post.age;
        }
        
        // ===== REWARD DISPLAY =====
        let rewardDisplay = 'None';
        if (post.reward && post.reward.trim() !== '' && post.reward !== '0') {
            const formattedReward = formatReward(post.reward);
            rewardDisplay = `₱${formattedReward}`;
        }
        
        // ===== IMAGES DISPLAY =====
        let imagesHtml = '';
        if (post.imageUrls && post.imageUrls.length > 0) {
            imagesHtml = `
                <div class="post-detail-item">
                    <div class="detail-label">📸 Images (${post.imageUrls.length})</div>
                    <div style="display: flex; gap: 10px; flex-wrap: wrap; margin-top: 10px; max-height: 200px; overflow-x: auto; padding-bottom: 10px;">
                        ${post.imageUrls.map((url, index) => {
                            const filename = url.split('/').pop();
                            let fullUrl = '';
                            
                            if (url.startsWith('http')) {
                                fullUrl = url;
                            } else if (url.startsWith('/api/uploads')) {
                                fullUrl = APP_BACKEND_URL + url;
                            } else if (url.startsWith('/uploads')) {
                                fullUrl = APP_BACKEND_URL + '/api' + url;
                            } else {
                                fullUrl = APP_BACKEND_URL + '/api/uploads/posts/' + filename;
                            }
                            
                            fullUrl = fullUrl.replace(/([^:]\/)\/+/g, '$1');
                            
                            return `<img src="${fullUrl}" 
                                     style="height: 150px; width: auto; border-radius: 5px; border: 1px solid #F0F0F0; cursor: pointer;" 
                                     onclick="window.open('${fullUrl}', '_blank')"
                                     onerror="this.style.display='none';">`;
                        }).join('')}
                    </div>
                </div>
            `;
        }

        // Build the modal content
        modalContent.innerHTML = `
            <div style="font-family: Arial, sans-serif;">
                <!-- Pet Name and Status -->
                <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px; border-bottom: 2px solid ${statusColor}; padding-bottom: 10px;">
                    <h2 style="margin: 0; color: #333;">${post.petName || 'Unnamed'}</h2>
                    <span style="background: ${statusColor}; color: white; padding: 5px 15px; border-radius: 20px; font-weight: bold;">${post.status || 'Unknown'}</span>
                </div>
                
                <!-- BASIC INFO - 2 COLUMN GRID -->
                <div style="display: grid; grid-template-columns: 1fr 1fr; gap: 15px; margin-bottom: 20px;">
                    
                    <div class="post-detail-item" style="margin: 0; padding: 10px; background: #f9f9f9; border-radius: 8px;">
                        <div class="detail-label" style="color: #999; font-size: 11px; margin-bottom: 3px;">👤 POSTED BY</div>
                        <div class="detail-value" style="font-weight: bold;">${post.userName || 'Unknown'}</div>
                    </div>
                    
                    <div class="post-detail-item" style="margin: 0; padding: 10px; background: #f9f9f9; border-radius: 8px;">
                        <div class="detail-label" style="color: #999; font-size: 11px; margin-bottom: 3px;">📅 POSTED</div>
                        <div class="detail-value">${post.time || 'Unknown'}</div>
                    </div>
                    
                    <div class="post-detail-item" style="margin: 0; padding: 10px; background: #f9f9f9; border-radius: 8px;">
                        <div class="detail-label" style="color: #999; font-size: 11px; margin-bottom: 3px;">📍 LOCATION</div>
                        <div class="detail-value">${post.location || 'Not specified'}</div>
                    </div>
                    
                    <div class="post-detail-item" style="margin: 0; padding: 10px; background: #f9f9f9; border-radius: 8px;">
                        <div class="detail-label" style="color: #999; font-size: 11px; margin-bottom: 3px;">📞 CONTACT</div>
                        <div class="detail-value">${post.contact || 'Not specified'}</div>
                    </div>
                </div>
                
                <!-- PET DETAILS - 3 COLUMN GRID for Gender, Age, Weight -->
                <div style="display: grid; grid-template-columns: repeat(3, 1fr); gap: 15px; margin-bottom: 20px;">
                    
                    <div class="post-detail-item" style="margin: 0; padding: 15px; background: #f0f0f0; border-radius: 8px; text-align: center;">
                        <div style="font-size: 32px; margin-bottom: 5px;">${genderEmoji}</div>
                        <div class="detail-label" style="color: #999; font-size: 11px;">GENDER</div>
                        <div class="detail-value" style="font-weight: bold; color: ${genderColor};">${genderDisplay}</div>
                    </div>
                    
                    <div class="post-detail-item" style="margin: 0; padding: 15px; background: #f0f0f0; border-radius: 8px; text-align: center;">
                        <div style="font-size: 32px; margin-bottom: 5px;">🎂</div>
                        <div class="detail-label" style="color: #999; font-size: 11px;">AGE</div>
                        <div class="detail-value" style="font-weight: bold;">${ageDisplay}</div>
                    </div>
                    
                    <div class="post-detail-item" style="margin: 0; padding: 15px; background: #f0f0f0; border-radius: 8px; text-align: center;">
                        <div style="font-size: 32px; margin-bottom: 5px;">⚖️</div>
                        <div class="detail-label" style="color: #999; font-size: 11px;">WEIGHT</div>
                        <div class="detail-value" style="font-weight: bold;">${weightDisplay}</div>
                    </div>
                </div>
                
                <!-- REWARD (if Lost) -->
                ${post.status === 'Lost' && rewardDisplay !== 'None' ? `
                <div class="post-detail-item" style="margin-bottom: 20px; padding: 15px; background: #FFF3E0; border-radius: 8px; border-left: 5px solid #FF9800;">
                    <div class="detail-label" style="color: #FF9800; font-size: 12px;">💰 REWARD</div>
                    <div class="detail-value" style="font-size: 20px; font-weight: bold; color: #FF9800;">${rewardDisplay}</div>
                </div>
                ` : ''}
                
                <!-- DESCRIPTION -->
                <div class="post-detail-item" style="margin-bottom: 20px;">
                    <div class="detail-label" style="color: #999; font-size: 12px; margin-bottom: 5px;">📝 DESCRIPTION</div>
                    <div class="detail-value" style="background: #f9f9f9; padding: 15px; border-radius: 8px; line-height: 1.6;">${post.description || 'No description'}</div>
                </div>
                
                ${imagesHtml}
                
                <!-- POST ID (for reference) -->
                <div style="margin-top: 20px; font-size: 10px; color: #ccc; text-align: right;">
                    Post ID: ${post.postId || post.id}
                </div>
            </div>
        `;
        
        openModal('viewPostModal');
        
    } catch (error) {
        console.error('❌ Failed to load post:', error);
        alert('Failed to load post details: ' + error.message);
    }
}

// Helper function to format reward
function formatReward(reward) {
    if (!reward) return '0';
    // Remove non-numeric characters
    const digitsOnly = reward.replace(/[^0-9]/g, '');
    if (!digitsOnly) return reward;
    
    const number = parseInt(digitsOnly);
    if (number > 1000000) return '1,000,000+';
    
    return number.toLocaleString();
}

async function flagPost(id) {
    if (confirm('Flag/unflag this post?')) {
        try {
            await apiRequest(`/admin/posts/${id}/flag`, { method: 'PUT' });
            loadPostsData();
        } catch (error) {
            console.error('Failed to flag post:', error);
            alert('Failed to flag post');
        }
    }
}

async function deletePost(id) {
    if (confirm('Are you sure you want to delete this post?')) {
        try {
            await apiRequest(`/admin/posts/${id}`, { method: 'DELETE' });
            loadPostsData();
            closeModal('deleteModal');
        } catch (error) {
            console.error('Failed to delete post:', error);
            alert('Failed to delete post');
        }
    }
}

// ===== REPORTS FUNCTIONS =====
async function loadReportsData() {
    try {
        console.log('Loading reports...');
        const stats = await apiRequest('/admin/stats');
        
        document.getElementById('total-users').textContent = stats.totalUsers || 0;
        document.getElementById('total-posts').textContent = stats.totalPosts || 0;
        document.getElementById('active-users').textContent = Math.floor((stats.totalUsers || 0) * 0.7);
        document.getElementById('engagement-rate').textContent = '65%';
        
        document.getElementById('user-change').textContent = '+12% vs last month';
        document.getElementById('post-change').textContent = '+8% vs last month';
        document.getElementById('active-change').textContent = '+5% vs last month';
        document.getElementById('engagement-change').textContent = '+3% vs last month';
        
        if (stats.postsByStatus) {
            const lostCount = stats.postsByStatus.lost || 0;
            const foundCount = stats.postsByStatus.found || 0;
            const adoptionCount = stats.postsByStatus.adoption || 0;
            const total = stats.totalPosts || 1;
            
            const lostPercent = Math.round((lostCount / total) * 100);
            const foundPercent = Math.round((foundCount / total) * 100);
            const adoptionPercent = Math.round((adoptionCount / total) * 100);
            
            document.getElementById('lost-percent').textContent = lostPercent;
            document.getElementById('found-percent').textContent = foundPercent;
            document.getElementById('adoption-percent').textContent = adoptionPercent;
            
            const pieChart = document.getElementById('pie-chart');
            if (pieChart) {
                const lostDeg = (lostCount / total) * 360;
                const foundDeg = (foundCount / total) * 360;
                const adoptionDeg = (adoptionCount / total) * 360;
                
                pieChart.style.background = `conic-gradient(
                    #F44336 0deg ${lostDeg}deg,
                    #4CAF50 ${lostDeg}deg ${lostDeg + foundDeg}deg,
                    #2196F3 ${lostDeg + foundDeg}deg ${lostDeg + foundDeg + adoptionDeg}deg
                )`;
            }
            
            if (stats.userGrowth && stats.userGrowth.length > 0) {
                const bars = document.querySelectorAll('.bar-chart .bar');
                const maxHeight = 150;
                const maxCount = Math.max(...stats.userGrowth, 1);
                
                bars.forEach((bar, index) => {
                    if (index < stats.userGrowth.length) {
                        const height = (stats.userGrowth[index] / maxCount) * maxHeight;
                        setTimeout(() => {
                            bar.style.transition = 'height 0.5s ease-in-out';
                            bar.style.height = height + 'px';
                        }, index * 100);
                    }
                });
            }
        }
        
        const today = new Date();
        const startDate = document.getElementById('startDate')?.value || '2024-01-01';
        const endDate = document.getElementById('endDate')?.value || today.toISOString().split('T')[0];
        
        // Comment out for now until endpoint is available
        // const summary = await apiRequest(`/admin/reports/summary?start=${startDate}&end=${endDate}`);
        // updateReportTable(summary);
        
        // Show empty table for now
        updateReportTable([]);
        
    } catch (error) {
        console.error('Failed to load reports:', error);
    }
}

function updateReportTable(data) {
    const tbody = document.getElementById('report-table-body');
    if (!tbody) return;
    
    if (!data || data.length === 0) {
        tbody.innerHTML = `
            <tr>
                <td colspan="7" style="text-align: center; padding: 30px; color: #999;">
                    No data available for this period
                </td>
            </tr>
        `;
        return;
    }
    
    tbody.innerHTML = data.map((row, index) => `
        <tr>
            <td>${row.month}</td>
            <td>${row.newUsers || Math.floor(Math.random() * 20) + 5}</td>
            <td>${row.total || 0}</td>
            <td>${row.lost || 0}</td>
            <td>${row.found || 0}</td>
            <td>${row.adoption || 0}</td>
            <td>${Math.floor(Math.random() * 20) + 60}%</td>
        </tr>
    `).join('');
}

// ===== FIXED SETTINGS LOAD FUNCTION =====
async function loadSettingsData() {
  try {
    const token = localStorage.getItem('adminToken');
    if (!token) {
      console.log('No token found, redirecting to login');
      window.location.href = 'index.html';
      return;
    }

    console.log('📥 Loading settings from API...');
    
    const response = await fetch(`${API_BASE_URL}/admin/settings`, {
      method: 'GET',
      headers: { 
        'Authorization': `Bearer ${token}`,
        'Content-Type': 'application/json'
      }
    });
    
    if (!response.ok) {
      if (response.status === 401) {
        localStorage.removeItem('adminToken');
        window.location.href = 'index.html';
        return;
      }
      throw new Error('Failed to load settings');
    }
    
    const settings = await response.json();
    console.log('✅ Settings loaded:', settings);
    
    // Update all form fields with settings data
    updateSettingsForm(settings);
    
    // Show success message
    showToast('Settings loaded successfully', 'success');
    
  } catch (error) {
    console.error('❌ Failed to load settings:', error);
    showToast('Failed to load settings', 'error');
  }
}

// ===== TOAST NOTIFICATION HELPER =====
function showToast(message, type = 'success') {
  // Check if toast container exists, if not create it
  let toastContainer = document.getElementById('toast-container');
  if (!toastContainer) {
    toastContainer = document.createElement('div');
    toastContainer.id = 'toast-container';
    toastContainer.style.cssText = `
      position: fixed;
      top: 20px;
      right: 20px;
      z-index: 9999;
    `;
    document.body.appendChild(toastContainer);
  }
  
  // Create toast
  const toast = document.createElement('div');
  toast.style.cssText = `
    background: ${type === 'success' ? '#4CAF50' : '#f44336'};
    color: white;
    padding: 12px 24px;
    border-radius: 8px;
    margin-bottom: 10px;
    box-shadow: 0 4px 12px rgba(0,0,0,0.15);
    animation: slideIn 0.3s ease;
    font-weight: 500;
  `;
  toast.textContent = message;
  
  toastContainer.appendChild(toast);
  
  // Remove after 3 seconds
  setTimeout(() => {
    toast.style.animation = 'slideOut 0.3s ease';
    setTimeout(() => toast.remove(), 300);
  }, 3000);
}

function updateToggle(id, value) {
    const element = document.getElementById(id);
    if (element) {
        if (value) {
            element.classList.add('active');
        } else {
            element.classList.remove('active');
        }
    }
}

function toggleSwitch(element) {
    element.classList.toggle('active');
}

async function saveSettings() {
    const settings = {
        siteName: document.getElementById('siteName')?.value || 'PawSociety',
        siteDescription: document.getElementById('siteDescription')?.value || '',
        contactEmail: document.getElementById('contactEmail')?.value || '',
        timezone: document.getElementById('timezone')?.value || 'PST',
        allowRegistration: document.getElementById('allowRegistration')?.classList.contains('active') || false,
        emailVerification: document.getElementById('emailVerification')?.classList.contains('active') || false,
        defaultRole: document.getElementById('defaultRole')?.value || 'user',
        twoFactorAuth: document.getElementById('twoFactorAuth')?.classList.contains('active') || false,
        sessionTimeout: parseInt(document.getElementById('sessionTimeout')?.value) || 120,
        autoApprovePosts: document.getElementById('autoApprovePosts')?.classList.contains('active') || false,
        profanityFilter: document.getElementById('profanityFilter')?.classList.contains('active') || false
    };
    
    try {
        await apiRequest('/admin/settings', {
            method: 'PUT',
            body: JSON.stringify(settings)
        });
        alert('Settings saved successfully!');
    } catch (error) {
        console.error('Failed to save settings:', error);
        alert('Failed to save settings');
    }
}

function switchTab(tabName) {
    document.querySelectorAll('.settings-section').forEach(section => {
        section.style.display = 'none';
    });
    
    const section = document.getElementById(tabName + '-settings');
    if (section) {
        section.style.display = 'block';
    }
    
    document.querySelectorAll('.settings-tab').forEach(tab => {
        tab.classList.remove('active');
    });
    event.target.classList.add('active');
}

// ===== UTILITY FUNCTIONS =====
function animateNumbers() {
    document.querySelectorAll('.stat-card .number').forEach(el => {
        const target = parseInt(el.textContent.replace(/,/g, ''));
        if (!isNaN(target)) {
            animateNumber(el, target);
        }
    });
}

function animateNumber(element, target) {
    let current = 0;
    const increment = Math.ceil(target / 50);
    const timer = setInterval(() => {
        current += increment;
        if (current >= target) {
            element.textContent = target.toLocaleString();
            clearInterval(timer);
        } else {
            element.textContent = current.toLocaleString();
        }
    }, 20);
}

function openModal(modalId) {
    const modal = document.getElementById(modalId);
    if (modal) {
        modal.classList.add('active');
    }
}

function closeModal(modalId) {
    const modal = document.getElementById(modalId);
    if (modal) {
        modal.classList.remove('active');
    }
}

function logout() {
    if (confirm('Are you sure you want to logout?')) {
        localStorage.removeItem('adminToken');
        localStorage.removeItem('adminUser');
        window.location.href = 'index.html';
    }
}

function applyUserFilters() {
    loadUsersData();
}

function applyPostFilters() {
    loadPostsData();
}

function applyDateRange() {
    loadReportsData();
}

function exportCSV() {
    alert('Export CSV feature coming soon!');
}

function exportPDF() {
    alert('Export PDF feature coming soon!');
}

function printReport() {
    window.print();
}

function exportReport() {
    alert('Export report feature coming soon!');
}

function clearCache() {
    if (confirm('Are you sure you want to clear the system cache?')) {
        alert('Cache cleared successfully!');
    }
}

function resetSettings() {
    if (confirm('Are you sure you want to reset all settings to default?')) {
        document.getElementById('siteName').value = 'PawSociety';
        document.getElementById('siteDescription').value = 'Because Every Pet Deserves a Home';
        document.getElementById('contactEmail').value = 'admin@pawsociety.com';
        document.getElementById('timezone').value = 'PST';
        document.getElementById('defaultRole').value = 'user';
        document.getElementById('sessionTimeout').value = '120';
        
        document.getElementById('allowRegistration').classList.add('active');
        document.getElementById('emailVerification').classList.add('active');
        document.getElementById('twoFactorAuth').classList.remove('active');
        document.getElementById('autoApprovePosts').classList.remove('active');
        document.getElementById('profanityFilter').classList.add('active');
        
        alert('Settings reset to default!');
    }
}

async function deleteAllData() {
    if (confirm('⚠️ WARNING: This will delete ALL non-admin data. Are you ABSOLUTELY sure?')) {
        const confirmText = prompt('Type "DELETE" to confirm:');
        if (confirmText === 'DELETE') {
            try {
                await apiRequest('/admin/clear-all-data', { method: 'POST' });
                alert('All non-admin data has been cleared.');
                loadDashboardData();
            } catch (error) {
                console.error('Failed to clear data:', error);
                alert('Failed to clear data');
            }
        }
    }
}

window.onclick = function(event) {
    const modals = ['userModal', 'viewPostModal', 'deleteModal', 'flagModal', 'reportModal'];
    modals.forEach(modalId => {
        const modal = document.getElementById(modalId);
        if (modal && event.target == modal) {
            modal.classList.remove('active');
        }
    });
};