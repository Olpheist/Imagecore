package com.green.imagecore.service.subscription;

import com.green.imagecore.entities.User;
import com.green.imagecore.entities.subscription.BillingEvent;
import com.green.imagecore.entities.subscription.SubscriptionTier;
import com.green.imagecore.entities.subscription.SubscriptionTierCode;
import com.green.imagecore.entities.subscription.UserSubscription;
import com.green.imagecore.repositories.subscription.BillingEventRepository;
import com.green.imagecore.repositories.subscription.SubscriptionTierRepository;
import com.green.imagecore.repositories.subscription.UserSubscriptionRepository;
import com.green.imagecore.service.UserService;
import com.stripe.model.*;
import com.stripe.model.checkout.Session;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StripeWebhookServiceTest {

    @Mock
    private BillingEventRepository billingEventRepository;

    @Mock
    private UserSubscriptionRepository userSubscriptionRepository;

    @Mock
    private SubscriptionTierRepository subscriptionTierRepository;

    @Mock
    private UserService userService;

    @InjectMocks
    private StripeWebhookService stripeWebhookService;

    @Test
    void handleEvent_duplicateEvent_skipsProcessing() {
        Event event = mock(Event.class);
        when(event.getId()).thenReturn("evt_123");
        when(event.getType()).thenReturn("invoice.paid");
        when(billingEventRepository.existsByProviderEventId("evt_123")).thenReturn(true);

        stripeWebhookService.handleEvent(event, "{\"id\":\"evt_123\"}");

        verify(billingEventRepository).existsByProviderEventId("evt_123");
        verify(billingEventRepository, never()).save(any(BillingEvent.class));
        verifyNoInteractions(userSubscriptionRepository, subscriptionTierRepository, userService);
    }

    @Test
    void handleEvent_checkoutSessionCompleted_updatesSubscription() throws Exception {
        Long userId = 1L;
        String providerSubscriptionId = "sub_123";
        String providerCustomerId = "cus_123";

        Event event = mock(Event.class);
        EventDataObjectDeserializer deserializer = mock(EventDataObjectDeserializer.class);
        Session session = mock(Session.class);

        when(event.getId()).thenReturn("evt_checkout");
        when(event.getType()).thenReturn("checkout.session.completed");
        when(event.getDataObjectDeserializer()).thenReturn(deserializer);

        when(billingEventRepository.existsByProviderEventId("evt_checkout")).thenReturn(false);
        when(deserializer.getObject()).thenReturn(Optional.of(session));

        when(session.getSubscription()).thenReturn(providerSubscriptionId);
        when(session.getCustomer()).thenReturn(providerCustomerId);

        HashMap<String, String> metadata = new HashMap<>();
        metadata.put("userId", String.valueOf(userId));
        when(session.getMetadata()).thenReturn(metadata);

        SubscriptionTier proTier = new SubscriptionTier();
        proTier.setId(2L);
        proTier.setCode(SubscriptionTierCode.PRO);

        User user = new User();
        user.setId(userId);

        UserSubscription existing = new UserSubscription();
        existing.setId(10L);
        existing.setUser(user);

        when(subscriptionTierRepository.findByCode(SubscriptionTierCode.PRO)).thenReturn(Optional.of(proTier));
        when(userSubscriptionRepository.findByUserId(userId)).thenReturn(Optional.of(existing));
        when(userService.findById(userId)).thenReturn(user);

        Subscription subscription = mock(Subscription.class);
        SubscriptionItem firstItem = mock(SubscriptionItem.class);
        com.stripe.model.Price price = mock(com.stripe.model.Price.class);
        SubscriptionItemCollectionMock items = new SubscriptionItemCollectionMock(List.of(firstItem));

        when(firstItem.getPrice()).thenReturn(price);
        when(price.getId()).thenReturn("price_123");
        when(firstItem.getCurrentPeriodStart()).thenReturn(1_700_000_000L);
        when(firstItem.getCurrentPeriodEnd()).thenReturn(1_700_002_000L);

        when(subscription.getItems()).thenReturn(items);
        when(subscription.getCancelAt()).thenReturn(1_700_003_000L);
        when(subscription.getCanceledAt()).thenReturn(1_700_004_000L);
        when(subscription.getEndedAt()).thenReturn(1_700_005_000L);
        when(subscription.getCancelAtPeriodEnd()).thenReturn(false);

        try (MockedStatic<Subscription> subscriptionMock = mockStatic(Subscription.class)) {
            subscriptionMock.when(() -> Subscription.retrieve(providerSubscriptionId)).thenReturn(subscription);

            stripeWebhookService.handleEvent(event, "{\"id\":\"evt_checkout\"}");
        }

        ArgumentCaptor<UserSubscription> subscriptionCaptor = ArgumentCaptor.forClass(UserSubscription.class);
        verify(userSubscriptionRepository).save(subscriptionCaptor.capture());

        UserSubscription saved = subscriptionCaptor.getValue();
        assertEquals(user, saved.getUser());
        assertEquals(proTier, saved.getTier());
        assertEquals(providerCustomerId, saved.getProviderCustomerId());
        assertEquals(providerSubscriptionId, saved.getProviderSubscriptionId());
        assertEquals("price_123", saved.getProviderPriceId());
        assertTrue(saved.isAutoRenew());
        assertEquals(Instant.ofEpochSecond(1_700_000_000L), saved.getCurrentPeriodStart());
        assertEquals(Instant.ofEpochSecond(1_700_002_000L), saved.getCurrentPeriodEnd());
        assertEquals(Instant.ofEpochSecond(1_700_003_000L), saved.getCancelAt());
        assertEquals(Instant.ofEpochSecond(1_700_004_000L), saved.getCanceledAt());
        assertEquals(Instant.ofEpochSecond(1_700_005_000L), saved.getEndedAt());

        verify(billingEventRepository, atLeast(2)).save(any(BillingEvent.class));
    }

    @Test
    void handleEvent_subscriptionUpdated_updatesExistingSubscription() {
        Event event = mock(Event.class);
        EventDataObjectDeserializer deserializer = mock(EventDataObjectDeserializer.class);
        Subscription subscription = mock(Subscription.class);
        SubscriptionItem firstItem = mock(SubscriptionItem.class);
        com.stripe.model.Price price = mock(com.stripe.model.Price.class);
        SubscriptionItemCollectionMock items = new SubscriptionItemCollectionMock(List.of(firstItem));

        when(event.getId()).thenReturn("evt_sub_updated");
        when(event.getType()).thenReturn("customer.subscription.updated");
        when(event.getDataObjectDeserializer()).thenReturn(deserializer);
        when(billingEventRepository.existsByProviderEventId("evt_sub_updated")).thenReturn(false);
        when(deserializer.getObject()).thenReturn(Optional.of(subscription));

        when(subscription.getId()).thenReturn("sub_123");
        when(subscription.getCustomer()).thenReturn("cus_123");
        when(subscription.getItems()).thenReturn(items);
        when(subscription.getCancelAtPeriodEnd()).thenReturn(true);
        when(subscription.getCancelAt()).thenReturn(1_700_003_000L);
        when(subscription.getCanceledAt()).thenReturn(1_700_004_000L);
        when(subscription.getEndedAt()).thenReturn(1_700_005_000L);

        when(firstItem.getCurrentPeriodStart()).thenReturn(1_700_000_000L);
        when(firstItem.getCurrentPeriodEnd()).thenReturn(1_700_002_000L);
        when(firstItem.getPrice()).thenReturn(price);
        when(price.getId()).thenReturn("price_123");

        UserSubscription existing = new UserSubscription();
        existing.setId(99L);

        when(userSubscriptionRepository.findByProviderSubscriptionId("sub_123")).thenReturn(Optional.of(existing));

        stripeWebhookService.handleEvent(event, "{\"id\":\"evt_sub_updated\"}");

        ArgumentCaptor<UserSubscription> captor = ArgumentCaptor.forClass(UserSubscription.class);
        verify(userSubscriptionRepository).save(captor.capture());

        UserSubscription saved = captor.getValue();
        assertEquals("cus_123", saved.getProviderCustomerId());
        assertEquals("price_123", saved.getProviderPriceId());
        assertFalse(saved.isAutoRenew());
        assertEquals(Instant.ofEpochSecond(1_700_000_000L), saved.getCurrentPeriodStart());
        assertEquals(Instant.ofEpochSecond(1_700_002_000L), saved.getCurrentPeriodEnd());
        assertEquals(Instant.ofEpochSecond(1_700_003_000L), saved.getCancelAt());
        assertEquals(Instant.ofEpochSecond(1_700_004_000L), saved.getCanceledAt());
        assertEquals(Instant.ofEpochSecond(1_700_005_000L), saved.getEndedAt());
    }

    @Test
    void handleEvent_subscriptionDeleted_downgradesToFree() {
        Event event = mock(Event.class);
        EventDataObjectDeserializer deserializer = mock(EventDataObjectDeserializer.class);
        Subscription subscription = mock(Subscription.class);

        when(event.getId()).thenReturn("evt_sub_deleted");
        when(event.getType()).thenReturn("customer.subscription.deleted");
        when(event.getDataObjectDeserializer()).thenReturn(deserializer);
        when(billingEventRepository.existsByProviderEventId("evt_sub_deleted")).thenReturn(false);
        when(deserializer.getObject()).thenReturn(Optional.of(subscription));

        when(subscription.getId()).thenReturn("sub_123");
        when(subscription.getCustomer()).thenReturn("cus_123");
        when(subscription.getCancelAt()).thenReturn(1_700_003_000L);
        when(subscription.getCanceledAt()).thenReturn(1_700_004_000L);
        when(subscription.getEndedAt()).thenReturn(1_700_005_000L);

        UserSubscription existing = new UserSubscription();
        existing.setId(50L);
        existing.setProviderSubscriptionId("sub_123");
        existing.setProviderPriceId("price_123");
        existing.setAutoRenew(true);
        existing.setCurrentPeriodStart(Instant.now());
        existing.setCurrentPeriodEnd(Instant.now());

        SubscriptionTier freeTier = new SubscriptionTier();
        freeTier.setId(1L);
        freeTier.setCode(SubscriptionTierCode.FREE);

        when(userSubscriptionRepository.findByProviderSubscriptionId("sub_123")).thenReturn(Optional.of(existing));
        when(subscriptionTierRepository.findByCode(SubscriptionTierCode.FREE)).thenReturn(Optional.of(freeTier));

        stripeWebhookService.handleEvent(event, "{\"id\":\"evt_sub_deleted\"}");

        ArgumentCaptor<UserSubscription> captor = ArgumentCaptor.forClass(UserSubscription.class);
        verify(userSubscriptionRepository).save(captor.capture());

        UserSubscription saved = captor.getValue();
        assertEquals(freeTier, saved.getTier());
        assertEquals("cus_123", saved.getProviderCustomerId());
        assertNull(saved.getProviderSubscriptionId());
        assertNull(saved.getProviderPriceId());
        assertNull(saved.getCurrentPeriodStart());
        assertNull(saved.getCurrentPeriodEnd());
        assertFalse(saved.isAutoRenew());
        assertEquals(Instant.ofEpochSecond(1_700_003_000L), saved.getCancelAt());
        assertEquals(Instant.ofEpochSecond(1_700_004_000L), saved.getCanceledAt());
        assertEquals(Instant.ofEpochSecond(1_700_005_000L), saved.getEndedAt());
    }

    @Test
    void handleEvent_invoicePaid_updatesPeriodDates() {
        Event event = mock(Event.class);
        EventDataObjectDeserializer deserializer = mock(EventDataObjectDeserializer.class);
        Invoice invoice = mock(Invoice.class);
        Invoice.Parent parent = mock(Invoice.Parent.class);
        Invoice.Parent.SubscriptionDetails subDetails = mock(Invoice.Parent.SubscriptionDetails.class);
        InvoiceLineItem firstLine = mock(InvoiceLineItem.class);
        InvoiceLineItem.Period period = mock(InvoiceLineItem.Period.class);
        InvoiceLineCollectionMock lines = new InvoiceLineCollectionMock(List.of(firstLine));

        when(event.getId()).thenReturn("evt_invoice_paid");
        when(event.getType()).thenReturn("invoice.paid");
        when(event.getDataObjectDeserializer()).thenReturn(deserializer);
        when(billingEventRepository.existsByProviderEventId("evt_invoice_paid")).thenReturn(false);
        when(deserializer.getObject()).thenReturn(Optional.of(invoice));

        when(invoice.getCustomer()).thenReturn("cus_123");
        when(invoice.getParent()).thenReturn(parent);
        when(parent.getSubscriptionDetails()).thenReturn(subDetails);
        when(subDetails.getSubscription()).thenReturn("sub_123");
        when(invoice.getLines()).thenReturn(lines);
        when(firstLine.getPeriod()).thenReturn(period);
        when(period.getStart()).thenReturn(1_700_000_000L);
        when(period.getEnd()).thenReturn(1_700_002_000L);

        UserSubscription existing = new UserSubscription();
        existing.setId(70L);

        when(userSubscriptionRepository.findByProviderSubscriptionId("sub_123")).thenReturn(Optional.of(existing));

        stripeWebhookService.handleEvent(event, "{\"id\":\"evt_invoice_paid\"}");

        ArgumentCaptor<UserSubscription> captor = ArgumentCaptor.forClass(UserSubscription.class);
        verify(userSubscriptionRepository).save(captor.capture());

        UserSubscription saved = captor.getValue();
        assertEquals("cus_123", saved.getProviderCustomerId());
        assertEquals(Instant.ofEpochSecond(1_700_000_000L), saved.getCurrentPeriodStart());
        assertEquals(Instant.ofEpochSecond(1_700_002_000L), saved.getCurrentPeriodEnd());
    }

    @Test
    void handleEvent_invoicePaymentFailed_updatesCustomerOnly() {
        Event event = mock(Event.class);
        EventDataObjectDeserializer deserializer = mock(EventDataObjectDeserializer.class);
        Invoice invoice = mock(Invoice.class);
        Invoice.Parent parent = mock(Invoice.Parent.class);
        Invoice.Parent.SubscriptionDetails subDetails = mock(Invoice.Parent.SubscriptionDetails.class);

        when(event.getId()).thenReturn("evt_invoice_failed");
        when(event.getType()).thenReturn("invoice.payment_failed");
        when(event.getDataObjectDeserializer()).thenReturn(deserializer);
        when(billingEventRepository.existsByProviderEventId("evt_invoice_failed")).thenReturn(false);
        when(deserializer.getObject()).thenReturn(Optional.of(invoice));

        when(invoice.getId()).thenReturn("in_456");
        when(invoice.getCustomer()).thenReturn("cus_456");
        when(invoice.getParent()).thenReturn(parent);
        when(parent.getSubscriptionDetails()).thenReturn(subDetails);
        when(subDetails.getSubscription()).thenReturn("sub_456");
        when(invoice.getStatus()).thenReturn("open");
        when(invoice.getBillingReason()).thenReturn("subscription_cycle");
        when(invoice.getAttemptCount()).thenReturn(2L);

        UserSubscription existing = new UserSubscription();
        existing.setId(71L);

        when(userSubscriptionRepository.findByProviderSubscriptionId("sub_456")).thenReturn(Optional.of(existing));

        stripeWebhookService.handleEvent(event, "{\"id\":\"evt_invoice_failed\"}");

        ArgumentCaptor<UserSubscription> captor = ArgumentCaptor.forClass(UserSubscription.class);
        verify(userSubscriptionRepository).save(captor.capture());

        UserSubscription saved = captor.getValue();
        assertEquals("cus_456", saved.getProviderCustomerId());
    }

    @Test
    void handleEvent_unhandledType_marksProcessedWithoutTouchingSubscriptions() {
        Event event = mock(Event.class);

        when(event.getId()).thenReturn("evt_unknown");
        when(event.getType()).thenReturn("some.random.event");
        when(billingEventRepository.existsByProviderEventId("evt_unknown")).thenReturn(false);

        stripeWebhookService.handleEvent(event, "{\"id\":\"evt_unknown\"}");

        verify(billingEventRepository, atLeast(2)).save(any(BillingEvent.class));
        verifyNoInteractions(userSubscriptionRepository, subscriptionTierRepository, userService);
    }

    @Test
    void handleEvent_checkoutSessionCompleted_missingSubscriptionId_returnsEarly() {
        Long userId = 1L;

        Event event = mock(Event.class);
        EventDataObjectDeserializer deserializer = mock(EventDataObjectDeserializer.class);
        Session session = mock(Session.class);

        when(event.getId()).thenReturn("evt_checkout_missing_sub");
        when(event.getType()).thenReturn("checkout.session.completed");
        when(event.getDataObjectDeserializer()).thenReturn(deserializer);
        when(billingEventRepository.existsByProviderEventId("evt_checkout_missing_sub")).thenReturn(false);
        when(deserializer.getObject()).thenReturn(Optional.of(session));

        when(session.getSubscription()).thenReturn(null);

        stripeWebhookService.handleEvent(event, "{\"id\":\"evt_checkout_missing_sub\"}");

        verify(userSubscriptionRepository, never()).save(any(UserSubscription.class));
        verify(subscriptionTierRepository, never()).findByCode(any());
        verify(userService, never()).findById(anyLong());
        verify(billingEventRepository, atLeast(2)).save(any(BillingEvent.class));
    }

    @Test
    void handleEvent_checkoutSessionCompleted_missingUserId_returnsEarly() {
        Event event = mock(Event.class);
        EventDataObjectDeserializer deserializer = mock(EventDataObjectDeserializer.class);
        Session session = mock(Session.class);

        when(event.getId()).thenReturn("evt_checkout_missing_user");
        when(event.getType()).thenReturn("checkout.session.completed");
        when(event.getDataObjectDeserializer()).thenReturn(deserializer);
        when(billingEventRepository.existsByProviderEventId("evt_checkout_missing_user")).thenReturn(false);
        when(deserializer.getObject()).thenReturn(Optional.of(session));

        when(session.getSubscription()).thenReturn("sub_123");
        when(session.getMetadata()).thenReturn(new HashMap<>());

        stripeWebhookService.handleEvent(event, "{\"id\":\"evt_checkout_missing_user\"}");

        verify(userSubscriptionRepository, never()).save(any(UserSubscription.class));
        verify(subscriptionTierRepository, never()).findByCode(any());
        verify(userService, never()).findById(anyLong());
        verify(billingEventRepository, atLeast(2)).save(any(BillingEvent.class));
    }

    @Test
    void handleEvent_checkoutSessionCompleted_subscriptionRetrieveFails_stillSavesBasicFields() throws Exception {
        Long userId = 1L;
        String providerSubscriptionId = "sub_123";
        String providerCustomerId = "cus_123";

        Event event = mock(Event.class);
        EventDataObjectDeserializer deserializer = mock(EventDataObjectDeserializer.class);
        Session session = mock(Session.class);

        when(event.getId()).thenReturn("evt_checkout_stripe_fail");
        when(event.getType()).thenReturn("checkout.session.completed");
        when(event.getDataObjectDeserializer()).thenReturn(deserializer);
        when(billingEventRepository.existsByProviderEventId("evt_checkout_stripe_fail")).thenReturn(false);
        when(deserializer.getObject()).thenReturn(Optional.of(session));

        when(session.getSubscription()).thenReturn(providerSubscriptionId);
        when(session.getCustomer()).thenReturn(providerCustomerId);

        HashMap<String, String> metadata = new HashMap<>();
        metadata.put("userId", String.valueOf(userId));
        when(session.getMetadata()).thenReturn(metadata);

        SubscriptionTier proTier = new SubscriptionTier();
        proTier.setId(2L);
        proTier.setCode(SubscriptionTierCode.PRO);

        User user = new User();
        user.setId(userId);

        UserSubscription existing = new UserSubscription();
        existing.setId(10L);

        when(subscriptionTierRepository.findByCode(SubscriptionTierCode.PRO)).thenReturn(Optional.of(proTier));
        when(userSubscriptionRepository.findByUserId(userId)).thenReturn(Optional.of(existing));
        when(userService.findById(userId)).thenReturn(user);

        try (MockedStatic<Subscription> subscriptionMock = mockStatic(Subscription.class)) {
            subscriptionMock.when(() -> Subscription.retrieve(providerSubscriptionId))
                    .thenThrow(new com.stripe.exception.ApiException("boom", null, null, 500, null));

            stripeWebhookService.handleEvent(event, "{\"id\":\"evt_checkout_stripe_fail\"}");
        }

        ArgumentCaptor<UserSubscription> captor = ArgumentCaptor.forClass(UserSubscription.class);
        verify(userSubscriptionRepository).save(captor.capture());

        UserSubscription saved = captor.getValue();
        assertEquals(user, saved.getUser());
        assertEquals(proTier, saved.getTier());
        assertEquals(providerCustomerId, saved.getProviderCustomerId());
        assertEquals(providerSubscriptionId, saved.getProviderSubscriptionId());
        assertTrue(saved.isAutoRenew());
        assertNull(saved.getProviderPriceId());
        assertNull(saved.getCurrentPeriodStart());
        assertNull(saved.getCurrentPeriodEnd());
    }

    @Test
    void handleEvent_subscriptionUpdated_missingUserSubscription_skipsSave() {
        Event event = mock(Event.class);
        EventDataObjectDeserializer deserializer = mock(EventDataObjectDeserializer.class);
        Subscription subscription = mock(Subscription.class);

        when(event.getId()).thenReturn("evt_sub_missing");
        when(event.getType()).thenReturn("customer.subscription.updated");
        when(event.getDataObjectDeserializer()).thenReturn(deserializer);
        when(billingEventRepository.existsByProviderEventId("evt_sub_missing")).thenReturn(false);
        when(deserializer.getObject()).thenReturn(Optional.of(subscription));

        when(subscription.getId()).thenReturn("sub_missing");
        when(userSubscriptionRepository.findByProviderSubscriptionId("sub_missing")).thenReturn(Optional.empty());

        stripeWebhookService.handleEvent(event, "{\"id\":\"evt_sub_missing\"}");

        verify(userSubscriptionRepository, never()).save(any(UserSubscription.class));
        verify(billingEventRepository, atLeast(2)).save(any(BillingEvent.class));
    }

    @Test
    void handleEvent_invoicePaid_missingSubscriptionId_returnsEarly() {
        Event event = mock(Event.class);
        EventDataObjectDeserializer deserializer = mock(EventDataObjectDeserializer.class);
        Invoice invoice = mock(Invoice.class);

        when(event.getId()).thenReturn("evt_invoice_no_sub");
        when(event.getType()).thenReturn("invoice.paid");
        when(event.getDataObjectDeserializer()).thenReturn(deserializer);
        when(billingEventRepository.existsByProviderEventId("evt_invoice_no_sub")).thenReturn(false);
        when(deserializer.getObject()).thenReturn(Optional.of(invoice));

        when(invoice.getParent()).thenReturn(null);

        stripeWebhookService.handleEvent(event, "{\"id\":\"evt_invoice_no_sub\"}");

        verify(userSubscriptionRepository, never()).save(any(UserSubscription.class));
        verify(billingEventRepository, atLeast(2)).save(any(BillingEvent.class));
    }

    @Test
    void handleEvent_invoicePaymentFailed_missingSubscriptionId_returnsEarly() {
        Event event = mock(Event.class);
        EventDataObjectDeserializer deserializer = mock(EventDataObjectDeserializer.class);
        Invoice invoice = mock(Invoice.class);

        when(event.getId()).thenReturn("evt_invoice_failed_no_sub");
        when(event.getType()).thenReturn("invoice.payment_failed");
        when(event.getDataObjectDeserializer()).thenReturn(deserializer);
        when(billingEventRepository.existsByProviderEventId("evt_invoice_failed_no_sub")).thenReturn(false);
        when(deserializer.getObject()).thenReturn(Optional.of(invoice));

        when(invoice.getId()).thenReturn("in_failed");
        when(invoice.getCustomer()).thenReturn("cus_123");
        when(invoice.getParent()).thenReturn(null);
        when(invoice.getStatus()).thenReturn("open");
        when(invoice.getBillingReason()).thenReturn("subscription_cycle");
        when(invoice.getAttemptCount()).thenReturn(1L);

        stripeWebhookService.handleEvent(event, "{\"id\":\"evt_invoice_failed_no_sub\"}");

        verify(userSubscriptionRepository, never()).save(any(UserSubscription.class));
        verify(billingEventRepository, atLeast(2)).save(any(BillingEvent.class));
    }

    @Test
    void handleEvent_subscriptionDeleted_nullEndedAt_setsEndedAtNow() {
        Event event = mock(Event.class);
        EventDataObjectDeserializer deserializer = mock(EventDataObjectDeserializer.class);
        Subscription subscription = mock(Subscription.class);

        when(event.getId()).thenReturn("evt_sub_deleted_null_end");
        when(event.getType()).thenReturn("customer.subscription.deleted");
        when(event.getDataObjectDeserializer()).thenReturn(deserializer);
        when(billingEventRepository.existsByProviderEventId("evt_sub_deleted_null_end")).thenReturn(false);
        when(deserializer.getObject()).thenReturn(Optional.of(subscription));

        when(subscription.getId()).thenReturn("sub_123");
        when(subscription.getCustomer()).thenReturn("cus_123");
        when(subscription.getCancelAt()).thenReturn(null);
        when(subscription.getCanceledAt()).thenReturn(null);
        when(subscription.getEndedAt()).thenReturn(null);

        UserSubscription existing = new UserSubscription();
        existing.setId(50L);
        existing.setProviderSubscriptionId("sub_123");

        SubscriptionTier freeTier = new SubscriptionTier();
        freeTier.setId(1L);
        freeTier.setCode(SubscriptionTierCode.FREE);

        when(userSubscriptionRepository.findByProviderSubscriptionId("sub_123")).thenReturn(Optional.of(existing));
        when(subscriptionTierRepository.findByCode(SubscriptionTierCode.FREE)).thenReturn(Optional.of(freeTier));

        Instant before = Instant.now();
        stripeWebhookService.handleEvent(event, "{\"id\":\"evt_sub_deleted_null_end\"}");
        Instant after = Instant.now();

        ArgumentCaptor<UserSubscription> captor = ArgumentCaptor.forClass(UserSubscription.class);
        verify(userSubscriptionRepository).save(captor.capture());

        UserSubscription saved = captor.getValue();
        assertNotNull(saved.getEndedAt());
        assertFalse(saved.getEndedAt().isBefore(before));
        assertFalse(saved.getEndedAt().isAfter(after));
    }

    @Test
    void handleEvent_subscriptionUpdated_usesDeserializeUnsafeFallback() throws Exception {
        Event event = mock(Event.class);
        EventDataObjectDeserializer deserializer = mock(EventDataObjectDeserializer.class);
        Subscription subscription = mock(Subscription.class);

        when(event.getId()).thenReturn("evt_sub_fallback");
        when(event.getType()).thenReturn("customer.subscription.updated");
        when(event.getDataObjectDeserializer()).thenReturn(deserializer);
        when(billingEventRepository.existsByProviderEventId("evt_sub_fallback")).thenReturn(false);

        when(deserializer.getObject()).thenReturn(Optional.empty());
        when(deserializer.deserializeUnsafe()).thenReturn(subscription);

        when(subscription.getId()).thenReturn("sub_123");
        when(subscription.getCustomer()).thenReturn("cus_123");
        when(subscription.getItems()).thenReturn(null);
        when(subscription.getCancelAtPeriodEnd()).thenReturn(false);
        when(subscription.getCancelAt()).thenReturn(null);
        when(subscription.getCanceledAt()).thenReturn(null);
        when(subscription.getEndedAt()).thenReturn(null);

        UserSubscription existing = new UserSubscription();
        existing.setId(99L);

        when(userSubscriptionRepository.findByProviderSubscriptionId("sub_123")).thenReturn(Optional.of(existing));

        stripeWebhookService.handleEvent(event, "{\"id\":\"evt_sub_fallback\"}");

        verify(userSubscriptionRepository).save(any(UserSubscription.class));
    }

    @Test
    void handleEvent_subscriptionUpdated_deserializeFailureThrowsIllegalStateException() throws Exception {
        Event event = mock(Event.class);
        EventDataObjectDeserializer deserializer = mock(EventDataObjectDeserializer.class);

        when(event.getId()).thenReturn("evt_sub_bad");
        when(event.getType()).thenReturn("customer.subscription.updated");
        when(event.getDataObjectDeserializer()).thenReturn(deserializer);
        when(billingEventRepository.existsByProviderEventId("evt_sub_bad")).thenReturn(false);

        when(deserializer.getObject()).thenReturn(Optional.empty());
        when(deserializer.getRawJson()).thenReturn("{bad json}");
        when(deserializer.deserializeUnsafe()).thenThrow(
                new com.stripe.exception.EventDataObjectDeserializationException(
                        "bad payload", "{bad json}"
                )
        );

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> stripeWebhookService.handleEvent(event, "{\"id\":\"evt_sub_bad\"}")
        );

        assertEquals(
                "Unable to deserialize Stripe subscription for event evt_sub_bad",
                exception.getMessage()
        );
    }

    private static final class SubscriptionItemCollectionMock extends SubscriptionItemCollection {
        private final List<SubscriptionItem> data;

        private SubscriptionItemCollectionMock(List<SubscriptionItem> data) {
            this.data = data;
        }

        @Override
        public List<SubscriptionItem> getData() {
            return data;
        }
    }

    private static final class InvoiceLineCollectionMock extends InvoiceLineItemCollection {
        private final List<InvoiceLineItem> data;

        private InvoiceLineCollectionMock(List<InvoiceLineItem> data) {
            this.data = data;
        }

        @Override
        public List<InvoiceLineItem> getData() {
            return data;
        }
    }
}