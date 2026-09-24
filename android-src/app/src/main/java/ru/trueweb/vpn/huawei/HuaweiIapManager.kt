package ru.trueweb.vpn.huawei

import android.app.Activity
import android.content.Intent
import android.content.IntentSender
import com.huawei.hms.iap.Iap
import com.huawei.hms.iap.IapClient
import com.huawei.hms.iap.entity.ConsumeOwnedPurchaseReq
import com.huawei.hms.iap.entity.InAppPurchaseData
import com.huawei.hms.iap.entity.OrderStatusCode
import com.huawei.hms.iap.entity.OwnedPurchasesReq
import com.huawei.hms.iap.entity.ProductInfoReq
import com.huawei.hms.iap.entity.PurchaseIntentReq

object HuaweiIapManager {
    const val PRODUCT_30_DAYS = "trueweb_30d"
    const val PRODUCT_EXTRA_DEVICE_30_DAYS = "trueweb_extra_device_30d"
    const val REQUEST_CODE_BUY = 59021

    data class Product(
        val id: String,
        val title: String,
        val price: String,
        val currency: String
    )

    data class PurchaseReceipt(
        val purchaseData: String,
        val signature: String,
        val productId: String,
        val purchaseToken: String
    )

    fun loadProducts(
        activity: Activity,
        onSuccess: (Map<String, Product>) -> Unit,
        onFailure: (Throwable) -> Unit
    ) {
        val request = ProductInfoReq().apply {
            priceType = IapClient.PriceType.IN_APP_CONSUMABLE
            productIds = arrayListOf(PRODUCT_30_DAYS, PRODUCT_EXTRA_DEVICE_30_DAYS)
        }
        Iap.getIapClient(activity)
            .obtainProductInfo(request)
            .addOnSuccessListener { result ->
                val products = result.productInfoList.orEmpty().associate { info ->
                    info.productId to Product(
                        id = info.productId,
                        title = info.productName.orEmpty(),
                        price = info.price.orEmpty(),
                        currency = info.currency.orEmpty()
                    )
                }
                onSuccess(products)
            }
            .addOnFailureListener(onFailure)
    }

    fun beginPurchase(
        activity: Activity,
        productId: String,
        developerPayload: String,
        onStarted: () -> Unit,
        onFailure: (Throwable) -> Unit
    ) {
        val request = PurchaseIntentReq().apply {
            this.productId = productId
            priceType = IapClient.PriceType.IN_APP_CONSUMABLE
            this.developerPayload = developerPayload
        }
        Iap.getIapClient(activity)
            .createPurchaseIntent(request)
            .addOnSuccessListener { result ->
                val status = result.status
                if (!status.hasResolution()) {
                    onFailure(IllegalStateException("Huawei IAP checkout is unavailable"))
                    return@addOnSuccessListener
                }
                try {
                    status.startResolutionForResult(activity, REQUEST_CODE_BUY)
                    onStarted()
                } catch (e: IntentSender.SendIntentException) {
                    onFailure(e)
                }
            }
            .addOnFailureListener(onFailure)
    }

    fun parsePurchaseResult(activity: Activity, data: Intent?): Result<PurchaseReceipt> = runCatching {
        requireNotNull(data) { "Huawei IAP returned no data" }
        val result = Iap.getIapClient(activity).parsePurchaseResultInfoFromIntent(data)
        when (result.returnCode) {
            OrderStatusCode.ORDER_STATE_SUCCESS -> {
                val purchaseData = result.inAppPurchaseData
                    ?.takeIf { it.isNotBlank() }
                    ?: error("Huawei IAP purchase data is empty")
                val signature = result.inAppDataSignature
                    ?.takeIf { it.isNotBlank() }
                    ?: error("Huawei IAP signature is empty")
                val parsed = InAppPurchaseData(purchaseData)
                PurchaseReceipt(
                    purchaseData = purchaseData,
                    signature = signature,
                    productId = parsed.productId,
                    purchaseToken = parsed.purchaseToken
                )
            }
            OrderStatusCode.ORDER_STATE_CANCEL -> error("PAYMENT_CANCELLED")
            OrderStatusCode.ORDER_PRODUCT_OWNED -> error("PRODUCT_OWNED")
            else -> error("Huawei IAP failed: ${result.returnCode}")
        }
    }

    fun loadUnconsumedPurchases(
        activity: Activity,
        onSuccess: (List<PurchaseReceipt>) -> Unit,
        onFailure: (Throwable) -> Unit
    ) {
        val request = OwnedPurchasesReq().apply {
            priceType = IapClient.PriceType.IN_APP_CONSUMABLE
        }
        Iap.getIapClient(activity)
            .obtainOwnedPurchases(request)
            .addOnSuccessListener { result ->
                val dataList = result.inAppPurchaseDataList.orEmpty()
                val signatures = result.inAppSignature.orEmpty()
                val receipts = buildList {
                    dataList.forEachIndexed { index, purchaseData ->
                        val signature = signatures.getOrNull(index).orEmpty()
                        if (signature.isBlank()) return@forEachIndexed
                        runCatching {
                            val parsed = InAppPurchaseData(purchaseData)
                            if (parsed.productId == PRODUCT_30_DAYS || parsed.productId == PRODUCT_EXTRA_DEVICE_30_DAYS) {
                                add(
                                    PurchaseReceipt(
                                        purchaseData = purchaseData,
                                        signature = signature,
                                        productId = parsed.productId,
                                        purchaseToken = parsed.purchaseToken
                                    )
                                )
                            }
                        }
                    }
                }
                onSuccess(receipts)
            }
            .addOnFailureListener(onFailure)
    }

    fun consume(
        activity: Activity,
        purchaseToken: String,
        onSuccess: () -> Unit,
        onFailure: (Throwable) -> Unit
    ) {
        val request = ConsumeOwnedPurchaseReq().apply {
            this.purchaseToken = purchaseToken
        }
        Iap.getIapClient(activity)
            .consumeOwnedPurchase(request)
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener(onFailure)
    }
}
