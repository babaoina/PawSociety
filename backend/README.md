# PawSociety Backend

Node.js + Express backend for PawSociety Android app with MongoDB database.

## Prerequisites

- **Node.js** (v16 or higher)
- **MongoDB** (local installation or MongoDB Atlas)
- **Firebase Project** with Authentication enabled

## Setup Instructions

### 1. Install MongoDB

**Windows:**
1. Download MongoDB Community Server from https://www.mongodb.com/try/download/community
2. Install with default settings
3. MongoDB will run as a Windows service automatically

**Verify MongoDB is running:**
```bash
mongosh
```

### 2. Setup Firebase Admin SDK

1. Go to [Firebase Console](https://console.firebase.google.com/)
2. Select your project
3. Go to **Project Settings** (gear icon)
4. Go to **Service Accounts** tab
5. Click **Generate New Private Key**
6. Save the JSON file as `firebase-service-account.json` in the `backend/` folder

### 3. Install Dependencies

```bash
cd backend
npm install
```

### 4. Configure Environment

Edit the `.env` file in the backend folder:

```env
PORT=5000
MONGODB_URI=mongodb://localhost:27017/pawsociety
FIREBASE_SERVICE_ACCOUNT_PATH=./firebase-service-account.json
```

### 5. Run the Backend Server

**Development mode (with auto-reload):**
```bash
npm run dev
```

**Production mode:**
```bash
npm start
```

The server will start on `http://localhost:5000`

## API Endpoints

### Image Upload
- `POST /api/upload/post` - Upload post images (multiple, max 5)
- `POST /api/upload/pet` - Upload pet image (single)
- `POST /api/upload/profile` - Upload profile picture (single)
- `DELETE /api/upload/:type/:filename` - Delete uploaded file

Uploaded images are stored in `uploads/` folder and served at `/api/uploads/`

### Authentication
- `POST /api/auth/firebase-login` - Login/Register with Firebase UID
- `POST /api/auth/verify-token` - Verify Firebase token

### Users
- `GET /api/users` - Get all users
- `GET /api/users/:firebaseUid` - Get user by UID
- `PUT /api/users/:firebaseUid` - Update user profile
- `DELETE /api/users/:firebaseUid` - Delete user

### Posts
- `GET /api/posts` - Get all posts (query: status, firebaseUid, limit, skip)
- `GET /api/posts/:postId` - Get single post
- `POST /api/posts` - Create new post
- `PUT /api/posts/:postId` - Update post
- `DELETE /api/posts/:postId` - Delete post
- `POST /api/posts/:postId/like` - Like/unlike post
- `GET /api/posts/:postId/is-liked` - Check like status

### Comments
- `GET /api/comments/post/:postId` - Get comments for post
- `POST /api/comments` - Add comment
- `DELETE /api/comments/:commentId` - Delete comment
- `POST /api/comments/:commentId/like` - Like/unlike comment

### Chat
- `GET /api/chat/conversations/:firebaseUid` - Get conversations
- `GET /api/chat/:chatId/messages` - Get messages in chat
- `POST /api/chat/send` - Send message
- `PUT /api/chat/messages/:messageId/read` - Mark message as read
- `PUT /api/chat/:chatId/read-all` - Mark all as read
- `DELETE /api/chat/messages/:messageId` - Delete message

### Pets
- `GET /api/pets` - Get all pets (query: ownerUid, type, limit, skip)
- `GET /api/pets/:petId` - Get single pet
- `POST /api/pets` - Add new pet
- `PUT /api/pets/:petId` - Update pet
- `DELETE /api/pets/:petId` - Delete pet

### Favorites
- `GET /api/favorites/:firebaseUid` - Get user's favorites
- `POST /api/favorites` - Add to favorites
- `DELETE /api/favorites/:postId` - Remove from favorites
- `GET /api/favorites/check/:postId` - Check if favorited

## Android Connection

### For Emulator
The Android app is configured to connect to:
```
http://10.0.2.2:5000/api/
```

### For Physical Device
1. Find your PC's local IP address:
   - Windows: `ipconfig` in Command Prompt
   - Look for IPv4 Address (e.g., 192.168.1.100)

2. Update `ApiClient.kt` in the Android app:
```kotlin
private const val BASE_URL = "http://192.168.x.x:5000/api/"
```

3. Make sure your PC and phone are on the same WiFi network

## Database Collections

The backend creates the following MongoDB collections:

- **users** - User accounts
- **posts** - Pet posts (Lost, Found, Adoption)
- **comments** - Post comments
- **chats** - Chat conversations
- **messages** - Chat messages
- **pets** - User's pets
- **favorites** - Favorite posts

## Testing the API

### Health Check
```bash
curl http://localhost:5000/api/health
```

### Example: Create User via Firebase Login
```bash
curl -X POST http://localhost:5000/api/auth/firebase-login \
  -H "Content-Type: application/json" \
  -d '{
    "firebaseUid": "test123",
    "email": "test@example.com",
    "username": "testuser",
    "fullName": "Test User"
  }'
```

## Troubleshooting

### MongoDB Connection Error
- Ensure MongoDB service is running
- Check MONGODB_URI in .env file
- Try: `net start MongoDB` (Windows)

### Port Already in Use
- Change PORT in .env file
- Or kill the process using port 5000

### Firebase Admin Error
- Ensure `firebase-service-account.json` is in the backend folder
- Check the path in .env file
- Verify Firebase project has Authentication enabled

## Project Structure

```
backend/
├── models/           # MongoDB schemas
│   ├── User.js
│   ├── Post.js
│   ├── Comment.js
│   ├── Chat.js
│   ├── Message.js
│   ├── Pet.js
│   └── Favorite.js
├── routes/           # API route handlers
│   ├── auth.js
│   ├── users.js
│   ├── posts.js
│   ├── comments.js
│   ├── chat.js
│   ├── pets.js
│   └── favorites.js
├── server.js         # Main server file
├── package.json
├── .env
└── .gitignore
```

## Security Notes

- In production, enable CORS only for specific origins
- Add rate limiting for API endpoints
- Use environment variables for sensitive data
- Enable MongoDB authentication
- Use HTTPS for production
