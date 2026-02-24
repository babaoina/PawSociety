const multer = require('multer');
const path = require('path');
const fs = require('fs');

// Ensure uploads directory exists
const uploadsDir = path.join(__dirname, '..', 'uploads');
const postsDir = path.join(uploadsDir, 'posts');
const petsDir = path.join(uploadsDir, 'pets');
const profilesDir = path.join(uploadsDir, 'profiles');

[uploadsDir, postsDir, petsDir, profilesDir].forEach(dir => {
  if (!fs.existsSync(dir)) {
    fs.mkdirSync(dir, { recursive: true });
  }
});

// Storage configuration
const storage = multer.diskStorage({
  destination: function (req, file, cb) {
    let uploadPath = uploadsDir;
    
    // Determine upload path based on route
    if (req.path.includes('/post')) {
      uploadPath = postsDir;
    } else if (req.path.includes('/pet')) {
      uploadPath = petsDir;
    } else if (req.path.includes('/profile')) {
      uploadPath = profilesDir;
    }
    
    cb(null, uploadPath);
  },
  filename: function (req, file, cb) {
    const uniqueSuffix = Date.now() + '-' + Math.round(Math.random() * 1E9);
    const ext = path.extname(file.originalname);
    cb(null, file.fieldname + '-' + uniqueSuffix + ext);
  }
});

// File filter - only allow images
const fileFilter = (req, file, cb) => {
  // Allow the request to proceed if no files are provided
  if (!file) {
    return cb(null, true);
  }
  
  // Check if it's an image based on mimetype
  if (file.mimetype.startsWith('image/')) {
    return cb(null, true);
  }
  
  // Also check common image extensions
  const allowedExtensions = ['.jpg', '.jpeg', '.png', '.gif', '.webp'];
  const ext = path.extname(file.originalname).toLowerCase();
  if (allowedExtensions.includes(ext)) {
    return cb(null, true);
  }
  
  // Reject non-image files
  cb(new Error('Only image files are allowed (jpeg, jpg, png, gif, webp)'));
};

// Multer configuration
const upload = multer({
  storage: storage,
  limits: {
    fileSize: 5 * 1024 * 1024 // 5MB limit
  },
  fileFilter: fileFilter
});

// Error handling middleware for multer
const handleMulterError = (err, req, res, next) => {
  if (err instanceof multer.MulterError) {
    if (err.code === 'LIMIT_FILE_SIZE') {
      return res.status(400).json({
        success: false,
        message: 'File size too large. Maximum size is 5MB'
      });
    }
    return res.status(400).json({
      success: false,
      message: err.message
    });
  } else if (err) {
    return res.status(400).json({
      success: false,
      message: err.message
    });
  }
  next();
};

module.exports = {
  upload,
  handleMulterError,
  uploadsDir
};
