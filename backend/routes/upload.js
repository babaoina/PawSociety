const express = require('express');
const router = express.Router();
const path = require('path');
const fs = require('fs');
const { upload, handleMulterError, uploadsDir } = require('../middleware/upload');

/**
 * POST /api/upload/post
 * Upload post images (multiple)
 */
router.post('/post', handleMulterError, upload.array('images', 5), (req, res) => {
  try {
    if (!req.files || req.files.length === 0) {
      return res.status(400).json({
        success: false,
        message: 'No files uploaded'
      });
    }

    const imageUrls = req.files.map(file => {
      return `/api/uploads/posts/${file.filename}`;
    });

    res.json({
      success: true,
      message: 'Images uploaded successfully',
      count: imageUrls.length,
      imageUrls
    });
  } catch (error) {
    console.error('Upload error:', error);
    res.status(500).json({
      success: false,
      message: error.message
    });
  }
});

/**
 * POST /api/upload/pet
 * Upload pet image (single)
 */
router.post('/pet', handleMulterError, upload.single('image'), (req, res) => {
  try {
    if (!req.file) {
      return res.status(400).json({
        success: false,
        message: 'No file uploaded'
      });
    }

    const imageUrl = `/api/uploads/pets/${req.file.filename}`;

    res.json({
      success: true,
      message: 'Image uploaded successfully',
      imageUrl
    });
  } catch (error) {
    console.error('Upload error:', error);
    res.status(500).json({
      success: false,
      message: error.message
    });
  }
});

/**
 * POST /api/upload/profile
 * Upload profile picture (single)
 */
router.post('/profile', handleMulterError, upload.single('image'), (req, res) => {
  try {
    if (!req.file) {
      return res.status(400).json({
        success: false,
        message: 'No file uploaded'
      });
    }

    const imageUrl = `/api/uploads/profiles/${req.file.filename}`;

    res.json({
      success: true,
      message: 'Profile picture uploaded successfully',
      imageUrl
    });
  } catch (error) {
    console.error('Upload error:', error);
    res.status(500).json({
      success: false,
      message: error.message
    });
  }
});

/**
 * DELETE /api/upload/:type/:filename
 * Delete an uploaded file
 */
router.delete('/:type/:filename', (req, res) => {
  try {
    const { type, filename } = req.params;
    
    // Validate type
    const allowedTypes = ['posts', 'pets', 'profiles'];
    if (!allowedTypes.includes(type)) {
      return res.status(400).json({
        success: false,
        message: 'Invalid file type'
      });
    }

    const filePath = path.join(uploadsDir, type, filename);

    // Check if file exists
    if (!fs.existsSync(filePath)) {
      return res.status(404).json({
        success: false,
        message: 'File not found'
      });
    }

    // Delete file
    fs.unlinkSync(filePath);

    res.json({
      success: true,
      message: 'File deleted successfully'
    });
  } catch (error) {
    console.error('Delete error:', error);
    res.status(500).json({
      success: false,
      message: error.message
    });
  }
});

module.exports = router;
