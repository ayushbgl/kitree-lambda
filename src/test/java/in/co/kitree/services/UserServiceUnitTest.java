package in.co.kitree.services;

import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.CollectionReference;
import in.co.kitree.pojos.FirebaseUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class UserServiceUnitTest {
    @Mock
    private Firestore mockDb;
    @Mock
    private DocumentReference mockDocRef;
    @Mock
    private DocumentSnapshot mockDocSnapshot;
    @Mock
    private CollectionReference mockCollectionRef;

    private static final String TEST_USER_ID = "test-user-id";

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        when(mockDb.collection("users")).thenReturn(mockCollectionRef);
    }

    @Test
    void testGetUserDetails_ExistingUser() throws ExecutionException, InterruptedException {
        // Arrange
        Map<String, Object> userData = new HashMap<>();
        userData.put("displayName", "Test User");
        userData.put("phoneNumber", "+1234567890");
        Map<String, String> referredBy = new HashMap<>();
        referredBy.put("id", "referrer-id");
        referredBy.put("name", "Referrer Name");
        userData.put("referredBy", referredBy);

        when(mockCollectionRef.document(TEST_USER_ID)).thenReturn(mockDocRef);
        when(mockDocRef.get()).thenReturn(com.google.api.core.ApiFutures.immediateFuture(mockDocSnapshot));
        when(mockDocSnapshot.exists()).thenReturn(true);
        when(mockDocSnapshot.getData()).thenReturn(userData);

        // Act
        FirebaseUser user = UserService.getUserDetails(mockDb, TEST_USER_ID);

        // Assert
        assertNotNull(user);
        assertEquals(TEST_USER_ID, user.getUid());
        assertEquals("Test User", user.getName());
        assertEquals("+1234567890", user.getPhoneNumber());
        assertEquals(referredBy, user.getReferredBy());
    }

    @Test
    void testGetUserDetails_NonExistingUser() throws ExecutionException, InterruptedException {
        // Arrange
        when(mockCollectionRef.document(TEST_USER_ID)).thenReturn(mockDocRef);
        when(mockDocRef.get()).thenReturn(com.google.api.core.ApiFutures.immediateFuture(mockDocSnapshot));
        when(mockDocSnapshot.exists()).thenReturn(false);

        // Act
        FirebaseUser user = UserService.getUserDetails(mockDb, TEST_USER_ID);

        // Assert
        assertNotNull(user);
        assertEquals(TEST_USER_ID, user.getUid());
        assertEquals("", user.getName());
        assertEquals("", user.getPhoneNumber());
        assertNotNull(user.getReferredBy());
        assertTrue(user.getReferredBy().isEmpty());
    }

    @Test
    void testGetUserDetails_NullFields() throws ExecutionException, InterruptedException {
        // Arrange
        Map<String, Object> userData = new HashMap<>();
        userData.put("displayName", null);
        userData.put("phoneNumber", null);
        userData.put("referredBy", null);

        when(mockCollectionRef.document(TEST_USER_ID)).thenReturn(mockDocRef);
        when(mockDocRef.get()).thenReturn(com.google.api.core.ApiFutures.immediateFuture(mockDocSnapshot));
        when(mockDocSnapshot.exists()).thenReturn(true);
        when(mockDocSnapshot.getData()).thenReturn(userData);

        // Act
        FirebaseUser user = UserService.getUserDetails(mockDb, TEST_USER_ID);

        // Assert
        assertNotNull(user);
        assertEquals(TEST_USER_ID, user.getUid());
        assertEquals("", user.getName());
        assertEquals("", user.getPhoneNumber());
        assertNotNull(user.getReferredBy());
        assertTrue(user.getReferredBy().isEmpty());
    }

    @Test
    void testGetUserDetails_MissingFields() throws ExecutionException, InterruptedException {
        // Arrange
        Map<String, Object> userData = new HashMap<>();
        // Intentionally not adding any fields

        when(mockCollectionRef.document(TEST_USER_ID)).thenReturn(mockDocRef);
        when(mockDocRef.get()).thenReturn(com.google.api.core.ApiFutures.immediateFuture(mockDocSnapshot));
        when(mockDocSnapshot.exists()).thenReturn(true);
        when(mockDocSnapshot.getData()).thenReturn(userData);

        // Act
        FirebaseUser user = UserService.getUserDetails(mockDb, TEST_USER_ID);

        // Assert
        assertNotNull(user);
        assertEquals(TEST_USER_ID, user.getUid());
        assertEquals("", user.getName());
        assertEquals("", user.getPhoneNumber());
        assertNotNull(user.getReferredBy());
        assertTrue(user.getReferredBy().isEmpty());
    }

    @Test
    void testGetUserDetails_InvalidReferredByType() throws ExecutionException, InterruptedException {
        // Arrange
        Map<String, Object> userData = new HashMap<>();
        userData.put("displayName", "Test User");
        userData.put("phoneNumber", "+1234567890");
        userData.put("referredBy", "invalid-type"); // Should be Map<String, String>

        when(mockCollectionRef.document(TEST_USER_ID)).thenReturn(mockDocRef);
        when(mockDocRef.get()).thenReturn(com.google.api.core.ApiFutures.immediateFuture(mockDocSnapshot));
        when(mockDocSnapshot.exists()).thenReturn(true);
        when(mockDocSnapshot.getData()).thenReturn(userData);

        // Act
        FirebaseUser user = UserService.getUserDetails(mockDb, TEST_USER_ID);

        // Assert
        assertNotNull(user);
        assertEquals(TEST_USER_ID, user.getUid());
        assertEquals("Test User", user.getName());
        assertEquals("+1234567890", user.getPhoneNumber());
        assertNotNull(user.getReferredBy());
        assertTrue(user.getReferredBy().isEmpty());
    }
} 