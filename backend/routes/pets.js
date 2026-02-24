const express = require('express');
const router = express.Router();
const Pet = require('../models/Pet');
const { v4: uuidv4 } = require('uuid');

/**
 * GET /api/pets
 * Get all pets with optional filters
 * Query: ownerUid, type, limit, skip
 */
router.get('/', async (req, res) => {
  try {
    const { ownerUid, type, limit = 50, skip = 0 } = req.query;
    
    const query = {};
    if (ownerUid) query.ownerUid = ownerUid;
    if (type) query.type = type;

    const pets = await Pet.find(query)
      .limit(parseInt(limit))
      .skip(parseInt(skip))
      .sort({ createdAt: -1 });

    res.json({
      success: true,
      count: pets.length,
      pets
    });
  } catch (error) {
    console.error('Get pets error:', error);
    res.status(500).json({
      success: false,
      message: error.message
    });
  }
});

/**
 * GET /api/pets/:petId
 * Get single pet by ID
 */
router.get('/:petId', async (req, res) => {
  try {
    const pet = await Pet.findOne({ petId: req.params.petId });

    if (!pet) {
      return res.status(404).json({
        success: false,
        message: 'Pet not found'
      });
    }

    res.json({
      success: true,
      pet
    });
  } catch (error) {
    console.error('Get pet error:', error);
    res.status(500).json({
      success: false,
      message: error.message
    });
  }
});

/**
 * POST /api/pets
 * Add a new pet
 * Body: { ownerUid, name, type, breed, description, imageUrl, age, gender }
 */
router.post('/', async (req, res) => {
  try {
    const { ownerUid, name, type, breed, description, imageUrl, age, gender } = req.body;

    if (!ownerUid || !name || !type || !description) {
      return res.status(400).json({
        success: false,
        message: 'ownerUid, name, type, and description are required'
      });
    }

    const pet = new Pet({
      petId: `pet_${Date.now()}_${uuidv4().substring(0, 8)}`,
      ownerUid,
      name,
      type,
      breed: breed || '',
      description,
      imageUrl: imageUrl || '',
      age: age || '',
      gender: gender || 'Unknown'
    });

    await pet.save();

    res.status(201).json({
      success: true,
      message: 'Pet added',
      data: pet
    });
  } catch (error) {
    console.error('Create pet error:', error);
    res.status(500).json({
      success: false,
      message: error.message
    });
  }
});

/**
 * PUT /api/pets/:petId
 * Update a pet
 */
router.put('/:petId', async (req, res) => {
  try {
    const { ownerUid } = req.body;
    const updateData = { ...req.body };
    delete updateData.ownerUid; // Don't allow changing owner

    const pet = await Pet.findOneAndUpdate(
      { petId: req.params.petId, ownerUid },
      updateData,
      { new: true, runValidators: true }
    );

    if (!pet) {
      return res.status(404).json({
        success: false,
        message: 'Pet not found or unauthorized'
      });
    }

    res.json({
      success: true,
      message: 'Pet updated',
      pet
    });
  } catch (error) {
    console.error('Update pet error:', error);
    res.status(500).json({
      success: false,
      message: error.message
    });
  }
});

/**
 * DELETE /api/pets/:petId
 * Delete a pet
 */
router.delete('/:petId', async (req, res) => {
  try {
    const { ownerUid } = req.body;

    const pet = await Pet.findOneAndDelete({
      petId: req.params.petId,
      ownerUid
    });

    if (!pet) {
      return res.status(404).json({
        success: false,
        message: 'Pet not found or unauthorized'
      });
    }

    res.json({
      success: true,
      message: 'Pet deleted'
    });
  } catch (error) {
    console.error('Delete pet error:', error);
    res.status(500).json({
      success: false,
      message: error.message
    });
  }
});

module.exports = router;
