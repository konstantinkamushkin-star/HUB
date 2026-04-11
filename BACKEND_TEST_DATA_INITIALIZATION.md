# Backend: Test Data Initialization API

## Overview
This document describes the backend API endpoint required to initialize test data (trips, instructors, courses) on the backend. This allows the iOS app to work with test data through the API instead of using local mock data.

## Endpoint

### POST /api/test/initialize

**Description**: Initializes test data on the backend. This endpoint should create test trips, instructors, courses, and other test entities in the database.

**Authentication**: Required (user must be authenticated)

**Request Body**: None (empty body)

**Response**:
```json
{
  "message": "Test data initialized successfully",
  "tripsCreated": 10,
  "instructorsCreated": 4,
  "coursesCreated": 10
}
```

**Response Fields**:
- `message` (string): Success message
- `tripsCreated` (number, optional): Number of trips created
- `instructorsCreated` (number, optional): Number of instructors created
- `coursesCreated` (number, optional): Number of courses created

**Status Codes**:
- `200 OK`: Test data initialized successfully
- `401 Unauthorized`: User not authenticated
- `403 Forbidden`: User doesn't have permission to initialize test data (optional - can allow all authenticated users in DEBUG mode)
- `500 Internal Server Error`: Server error during initialization

## Implementation Details

### Test Data Structure

The backend should create test data matching the structure defined in the iOS app's `TestData.swift` file. Key entities:

1. **Test Dive Center**:
   - ID: `"test-dive-center-1"`
   - Name: `"test dive"` (or similar)
   - Should be linked to the authenticated user's `diveCenterId` if they are a dive center admin

2. **Test Trips**:
   - Should have `organizerId` matching the test dive center ID or the user's `diveCenterId`
   - Should have `organizerType: "dive_center"`
   - Should include various trip types (daily, safari)
   - Should include trips with different dates (upcoming and archived)

3. **Test Instructors**:
   - Should have `diveCenterId` matching the test dive center ID or the user's `diveCenterId`
   - Should include various certification levels
   - Should include different training systems (PADI, SSI, NAUI)

4. **Test Courses**:
   - Should have `diveCenterId` matching the test dive center ID or the user's `diveCenterId`
   - Should include various course levels (basic, advanced, professional, technical, specialization)
   - Should include different training systems

### Important Notes

1. **Idempotency**: The endpoint should be idempotent. If test data already exists, it should either:
   - Return success without creating duplicates, OR
   - Delete existing test data and recreate it, OR
   - Update existing test data

2. **User Association**: Test data should be associated with the authenticated user's dive center (if they are a dive center admin) or with a test dive center that the user has access to.

3. **Environment**: This endpoint should only be available in development/staging environments, not in production.

4. **Data Cleanup**: Consider providing a cleanup endpoint `DELETE /api/test/cleanup` to remove test data when needed.

## Example Implementation (Node.js/Express)

```javascript
router.post('/test/initialize', authenticateToken, async (req, res) => {
  try {
    const userId = req.user.id;
    const user = await User.findById(userId);
    
    // Only allow in development/staging
    if (process.env.NODE_ENV === 'production') {
      return res.status(403).json({ message: 'Test data initialization not available in production' });
    }
    
    // Get or create test dive center
    let testDiveCenter = await DiveCenter.findOne({ id: 'test-dive-center-1' });
    if (!testDiveCenter) {
      testDiveCenter = await DiveCenter.create({
        id: 'test-dive-center-1',
        name: 'test dive',
        // ... other fields
      });
    }
    
    // Use user's diveCenterId if they are a dive center admin
    const diveCenterId = user.role === 'DIVE_CENTER_ADMIN' && user.diveCenterId 
      ? user.diveCenterId 
      : testDiveCenter.id;
    
    // Create test trips
    const tripsCreated = await createTestTrips(diveCenterId);
    
    // Create test instructors
    const instructorsCreated = await createTestInstructors(diveCenterId);
    
    // Create test courses
    const coursesCreated = await createTestCourses(diveCenterId);
    
    res.json({
      message: 'Test data initialized successfully',
      tripsCreated,
      instructorsCreated,
      coursesCreated
    });
  } catch (error) {
    console.error('Error initializing test data:', error);
    res.status(500).json({ message: 'Failed to initialize test data', error: error.message });
  }
});
```

## iOS App Integration

The iOS app will:
1. Call this endpoint once per session when entering admin views (TripsManagementView, InstructorManagementView)
2. Store a flag in UserDefaults to prevent multiple calls
3. Fall back to local test data if the endpoint is not available or fails
4. Use the initialized test data through normal API endpoints (GET /api/trips, GET /api/instructors, etc.)

## Testing

To test the endpoint:
1. Authenticate as a dive center admin
2. Call `POST /api/test/initialize`
3. Verify that test trips, instructors, and courses are created
4. Verify that they are associated with the correct dive center
5. Verify that they can be retrieved through normal API endpoints
