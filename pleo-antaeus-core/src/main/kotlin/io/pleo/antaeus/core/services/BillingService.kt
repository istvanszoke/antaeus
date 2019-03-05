package io.pleo.antaeus.core.services

import io.pleo.antaeus.core.exceptions.CurrencyMismatchException
import io.pleo.antaeus.core.exceptions.CustomerNotFoundException
import io.pleo.antaeus.core.exceptions.NetworkException
import io.pleo.antaeus.core.external.PaymentProvider
import io.pleo.antaeus.models.Invoice
import io.pleo.antaeus.models.InvoiceStatus
import mu.KotlinLogging
import java.lang.Exception
import java.util.*
import kotlin.concurrent.schedule

private val logger = KotlinLogging.logger {}

/**
 * This service takes care of billing the customers each month
 * @param paymentProvider - a service that handles the actual payments of invoices
 * @param invoiceService - a service that fetched the invoices
 * @param pendingPeriodUnit - the unit of the amount of time between two PENDING billing cycles
 * @param pendingPeriodAmount - the amount of time between two PENDING billing cycles
 * @param failedPeriodUnit - the unit of the amount of time between two FAILED (RETRY) billing cycles
 * @param failedPeriodAmount - the amount of time between two FAILED (RETRY) billing cycles
 * @param firstOfMonth - a boolean which indicates that a PENDING billing cycle always starts at the first day of the month
 */
class BillingService(
        private val paymentProvider: PaymentProvider,
        private val invoiceService: InvoiceService,
        private val pendingPeriodUnit: Int,
        private val pendingPeriodAmount: Int,
        private val failedPeriodUnit: Int,
        private val failedPeriodAmount: Int,
        private val firstOfMonth: Boolean
) {
    private val timer: Timer = Timer("billingScheduler", true)

    private var currentState = BillingState.PENDING

    fun startService() {
        scheduleNext()
    }

    private fun scheduleNext() {
        val time = GregorianCalendar.getInstance()
        when (currentState) {
            BillingState.PENDING -> {
                val scheduledTime = getNextPendingDate(time.time)
                timer.schedule(scheduledTime) {
                    makePayment(InvoiceStatus.PENDING)
                    scheduleNext()
                }
                currentState = BillingState.FAILED1
                logger.info("New billing job has been scheduled for: $scheduledTime")
            }
            BillingState.FAILED1 -> {
                val scheduledTime = getNextFailedDate(time.time)
                timer.schedule(scheduledTime) {
                    makePayment(InvoiceStatus.FAILED1)
                    scheduleNext()
                }
                currentState = BillingState.FAILED2
                logger.info("New billing job has been scheduled for: $scheduledTime")
            }
            BillingState.FAILED2 -> {
                val scheduledTime = getNextFailedDate(time.time)
                timer.schedule(scheduledTime) {
                    makePayment(InvoiceStatus.FAILED2)
                    scheduleNext()
                }
                currentState = BillingState.FAILED3
                logger.info("New billing job has been scheduled for: $scheduledTime")
            }
            BillingState.FAILED3 -> {
                val scheduledTime = getNextFailedDate(time.time)
                timer.schedule(scheduledTime) {
                    makePayment(InvoiceStatus.FAILED3)
                    scheduleNext()
                }
                currentState = BillingState.PENDING
                logger.info("New billing job has been scheduled for: $scheduledTime")
            }
        }

    }

    private fun getNextPendingDate(date: Date): Date {
        val scheduledTime = GregorianCalendar()
        scheduledTime.time = date
        if (firstOfMonth) {
            scheduledTime.set(Calendar.DAY_OF_MONTH, 1)
            scheduledTime.set(Calendar.HOUR_OF_DAY, 8)
            scheduledTime.set(Calendar.MINUTE, 0)
        }
        scheduledTime.add(pendingPeriodUnit, pendingPeriodAmount)
        return scheduledTime.time
    }

    private fun getNextFailedDate(date: Date): Date {
        val scheduledTime = GregorianCalendar()
        scheduledTime.time = date
        scheduledTime.add(failedPeriodUnit, failedPeriodAmount)
        return scheduledTime.time
    }

    private fun makePayment(status: InvoiceStatus) {
        val invoices = invoiceService.fetchOfStatus(status)
        for (invoice in invoices) {
            var result = false
            var paymentException: Exception? = null
            try {
                result = paymentProvider.charge(invoice)
            } catch (e: Exception) {
                logger.debug("Failed to charge client due to Exception", e)
                paymentException = e
            } finally {
                handleChargeResult(invoice, result, paymentException)
            }
        }
    }

    private fun handleChargeResult(invoice: Invoice, result: Boolean, exception: Exception?) {
        var invoiceToSave = invoice.copy()

        if (result && exception == null) {
            invoiceToSave = invoice.copy(status = InvoiceStatus.PAID)
        } else if (exception != null) {
            when (exception) {
                is CurrencyMismatchException -> invoiceToSave = invoice.copy(status = InvoiceStatus.MANUAL_CHECK)
                is CustomerNotFoundException -> invoiceToSave = invoice.copy(status = InvoiceStatus.MANUAL_CHECK)
                is NetworkException -> invoiceToSave = handleFalsePaymentProviderResponse(invoice)
            }
        } else {
            invoiceToSave = handleFalsePaymentProviderResponse(invoice)
        }

        invoiceService.update(invoiceToSave)
    }

    private fun handleFalsePaymentProviderResponse(invoice: Invoice): Invoice {
        var invoiceToReturn = invoice.copy()
        when (invoice.status) {
            InvoiceStatus.PENDING -> invoiceToReturn = invoice.copy(status = InvoiceStatus.FAILED1)
            InvoiceStatus.FAILED1 -> invoiceToReturn = invoice.copy(status = InvoiceStatus.FAILED2)
            InvoiceStatus.FAILED2 -> invoiceToReturn = invoice.copy(status = InvoiceStatus.FAILED3)
            InvoiceStatus.FAILED3 -> invoiceToReturn = invoice.copy(status = InvoiceStatus.MANUAL_CHECK)
            else -> {
                logger.warn("Illegal state of invoice")
            }
        }
        return invoiceToReturn
    }
}