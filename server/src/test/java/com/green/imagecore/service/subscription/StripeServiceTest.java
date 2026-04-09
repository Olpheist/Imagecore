package com.green.imagecore.service.subscription;

import com.green.imagecore.config.StripeConfig;
import com.green.imagecore.entities.User;
import com.green.imagecore.entities.subscription.UserSubscription;
import com.green.imagecore.exception.ResourceNotFoundException;
import com.green.imagecore.repositories.subscription.UserSubscriptionRepository;
import com.stripe.model.Subscription;
import com.stripe.model.checkout.Session;
import com.stripe.param.checkout.SessionCreateParams;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StripeServiceTest {

    @Mock
    private StripeConfig stripeConfig;

    @Mock
    private UserSubscriptionRepository userSubscriptionRepository;

    @InjectMocks
    private StripeService stripeService;

    @Test
    void createCheckoutSession_Success() throws Exception {
        Long userId = 1L;

        when(stripeConfig.getSuccessUrl()).thenReturn("http://localhost:3000/billing/success");
        when(stripeConfig.getCancelUrl()).thenReturn("http://localhost:3000/billing/cancel");
        when(stripeConfig.getProMonthlyPriceId()).thenReturn("price_test_123");

        Session session = mock(Session.class);

        try (MockedStatic<Session> sessionMock = mockStatic(Session.class)) {
            sessionMock.when(() -> Session.create(any(SessionCreateParams.class))).thenReturn(session);

            Session result = stripeService.createCheckoutSession(userId);

            assertNotNull(result);
            assertEquals(session, result);

            verify(stripeConfig).getSuccessUrl();
            verify(stripeConfig).getCancelUrl();
            verify(stripeConfig).getProMonthlyPriceId();
            sessionMock.verify(() -> Session.create(any(SessionCreateParams.class)), times(1));
        }
    }

    @Test
    void cancelSubscription_Success() throws Exception {
        Long userId = 1L;
        String providerSubscriptionId = "sub_123";
        long cancelAtEpoch = 1_800_000_000L;

        User user = new User();
        user.setId(userId);

        UserSubscription userSubscription = new UserSubscription();
        userSubscription.setUser(user);
        userSubscription.setProviderSubscriptionId(providerSubscriptionId);
        userSubscription.setAutoRenew(true);

        Subscription subscription = mock(Subscription.class);
        Subscription updatedSubscription = mock(Subscription.class);

        when(userSubscriptionRepository.findByUserId(userId)).thenReturn(Optional.of(userSubscription));
        when(updatedSubscription.getCancelAt()).thenReturn(cancelAtEpoch);

        try (MockedStatic<Subscription> subscriptionMock = mockStatic(Subscription.class)) {
            subscriptionMock.when(() -> Subscription.retrieve(providerSubscriptionId)).thenReturn(subscription);
            when(subscription.update(Map.of("cancel_at_period_end", true))).thenReturn(updatedSubscription);

            stripeService.cancelSubscription(userId);

            ArgumentCaptor<UserSubscription> captor = ArgumentCaptor.forClass(UserSubscription.class);
            verify(userSubscriptionRepository).save(captor.capture());

            UserSubscription saved = captor.getValue();
            assertFalse(saved.isAutoRenew());
            assertEquals(Instant.ofEpochSecond(cancelAtEpoch), saved.getCancelAt());

            subscriptionMock.verify(() -> Subscription.retrieve(providerSubscriptionId), times(1));
            verify(subscription).update(Map.of("cancel_at_period_end", true));
        }
    }

    @Test
    void cancelSubscription_ThrowsException_WhenSubscriptionNotFound() {
        Long userId = 99L;

        when(userSubscriptionRepository.findByUserId(userId)).thenReturn(Optional.empty());

        ResourceNotFoundException exception = assertThrows(
                ResourceNotFoundException.class,
                () -> stripeService.cancelSubscription(userId)
        );

        assertEquals("Subscription not found for user 99", exception.getMessage());
        verify(userSubscriptionRepository, never()).save(any());
    }

    @Test
    void cancelSubscription_ThrowsException_WhenProviderSubscriptionIdMissing() {
        Long userId = 1L;

        User user = new User();
        user.setId(userId);

        UserSubscription userSubscription = new UserSubscription();
        userSubscription.setUser(user);
        userSubscription.setProviderSubscriptionId(null);

        when(userSubscriptionRepository.findByUserId(userId)).thenReturn(Optional.of(userSubscription));

        ResourceNotFoundException exception = assertThrows(
                ResourceNotFoundException.class,
                () -> stripeService.cancelSubscription(userId)
        );

        assertEquals("No Stripe subscription found for user 1", exception.getMessage());
        verify(userSubscriptionRepository, never()).save(any());
    }

    @Test
    void resumeSubscription_Success() throws Exception {
        Long userId = 1L;
        String providerSubscriptionId = "sub_123";

        User user = new User();
        user.setId(userId);

        UserSubscription userSubscription = new UserSubscription();
        userSubscription.setUser(user);
        userSubscription.setProviderSubscriptionId(providerSubscriptionId);
        userSubscription.setAutoRenew(false);
        userSubscription.setCancelAt(Instant.now());

        Subscription subscription = mock(Subscription.class);

        when(userSubscriptionRepository.findByUserId(userId)).thenReturn(Optional.of(userSubscription));

        try (MockedStatic<Subscription> subscriptionMock = mockStatic(Subscription.class)) {
            subscriptionMock.when(() -> Subscription.retrieve(providerSubscriptionId)).thenReturn(subscription);
            when(subscription.update(Map.of("cancel_at_period_end", false))).thenReturn(subscription);

            stripeService.resumeSubscription(userId);

            ArgumentCaptor<UserSubscription> captor = ArgumentCaptor.forClass(UserSubscription.class);
            verify(userSubscriptionRepository).save(captor.capture());

            UserSubscription saved = captor.getValue();
            assertTrue(saved.isAutoRenew());
            assertNull(saved.getCancelAt());

            subscriptionMock.verify(() -> Subscription.retrieve(providerSubscriptionId), times(1));
            verify(subscription).update(Map.of("cancel_at_period_end", false));
        }
    }

    @Test
    void deleteSubscription_Success_WithEndedAtFromStripe() throws Exception {
        Long userId = 1L;
        String providerSubscriptionId = "sub_123";
        long endedAtEpoch = 1_900_000_000L;

        User user = new User();
        user.setId(userId);

        UserSubscription userSubscription = new UserSubscription();
        userSubscription.setUser(user);
        userSubscription.setProviderSubscriptionId(providerSubscriptionId);
        userSubscription.setAutoRenew(true);

        Subscription subscription = mock(Subscription.class);
        Subscription deletedSubscription = mock(Subscription.class);

        when(userSubscriptionRepository.findByUserId(userId)).thenReturn(Optional.of(userSubscription));
        when(deletedSubscription.getEndedAt()).thenReturn(endedAtEpoch);

        try (MockedStatic<Subscription> subscriptionMock = mockStatic(Subscription.class)) {
            subscriptionMock.when(() -> Subscription.retrieve(providerSubscriptionId)).thenReturn(subscription);
            when(subscription.cancel()).thenReturn(deletedSubscription);

            stripeService.deleteSubscription(userId);

            ArgumentCaptor<UserSubscription> captor = ArgumentCaptor.forClass(UserSubscription.class);
            verify(userSubscriptionRepository).save(captor.capture());

            UserSubscription saved = captor.getValue();
            assertFalse(saved.isAutoRenew());
            assertNull(saved.getCancelAt());
            assertNotNull(saved.getCanceledAt());
            assertEquals(Instant.ofEpochSecond(endedAtEpoch), saved.getEndedAt());

            subscriptionMock.verify(() -> Subscription.retrieve(providerSubscriptionId), times(1));
            verify(subscription).cancel();
        }
    }

    @Test
    void deleteSubscription_Success_WhenStripeEndedAtIsNull() throws Exception {
        Long userId = 1L;
        String providerSubscriptionId = "sub_123";

        User user = new User();
        user.setId(userId);

        UserSubscription userSubscription = new UserSubscription();
        userSubscription.setUser(user);
        userSubscription.setProviderSubscriptionId(providerSubscriptionId);

        Subscription subscription = mock(Subscription.class);
        Subscription deletedSubscription = mock(Subscription.class);

        when(userSubscriptionRepository.findByUserId(userId)).thenReturn(Optional.of(userSubscription));
        when(deletedSubscription.getEndedAt()).thenReturn(null);

        try (MockedStatic<Subscription> subscriptionMock = mockStatic(Subscription.class)) {
            subscriptionMock.when(() -> Subscription.retrieve(providerSubscriptionId)).thenReturn(subscription);
            when(subscription.cancel()).thenReturn(deletedSubscription);

            stripeService.deleteSubscription(userId);

            ArgumentCaptor<UserSubscription> captor = ArgumentCaptor.forClass(UserSubscription.class);
            verify(userSubscriptionRepository).save(captor.capture());

            UserSubscription saved = captor.getValue();
            assertFalse(saved.isAutoRenew());
            assertNull(saved.getCancelAt());
            assertNotNull(saved.getCanceledAt());
            assertNotNull(saved.getEndedAt());

            verify(subscription).cancel();
        }
    }
}