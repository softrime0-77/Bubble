package com.bubble.game.billing

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ConsumeParams
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.consumePurchase
import com.android.billingclient.api.queryProductDetails
import com.android.billingclient.api.queryPurchasesAsync
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Google Play 인앱 결제 (소모성 상품: 포인트).
 *
 * 흐름: 결제 → [onPurchased]로 서버에 영수증 확인 요청 → 서버가 점수 지급 → 그다음 소모(consume).
 * 서버 확인 전에 앱이 꺼져도, 다음 실행 때 [refreshPending]이 남은 결제를 다시 확인합니다.
 */
class BillingManager(
    context: Context,
    private val scope: CoroutineScope,
    /** 서버에 영수증 확인을 요청하고, 지급에 성공하면 true */
    private val onPurchased: suspend (productId: String, token: String) -> Boolean,
) {
    companion object {
        /** Play Console에 만들 소모성 상품 ID (functions/src/index.ts의 PRODUCTS와 같게) */
        const val POINTS_500 = "points_500"
        const val POINTS_500_AMOUNT = 500
    }

    private val _price = MutableStateFlow<String?>(null)
    /** 스토어에 표시된 가격 (예: ₩500). null이면 아직 구매할 수 없음 */
    val price: StateFlow<String?> = _price.asStateFlow()

    private var details: ProductDetails? = null

    private val client: BillingClient = BillingClient.newBuilder(context.applicationContext)
        .setListener { result, purchases -> onUpdated(result, purchases) }
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .build()

    fun start() {
        if (client.isReady) return
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    scope.launch {
                        loadProduct()
                        refreshPending()
                    }
                }
            }

            override fun onBillingServiceDisconnected() {
                _price.value = null
            }
        })
    }

    private suspend fun loadProduct() {
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(POINTS_500)
                        .setProductType(BillingClient.ProductType.INAPP)
                        .build(),
                ),
            ).build()
        val result = client.queryProductDetails(params)
        details = result.productDetailsList?.firstOrNull()
        _price.value = details?.oneTimePurchaseOfferDetails?.formattedPrice
    }

    /** 결제 창 띄우기. 상품을 못 불러왔으면 false. */
    fun buy(activity: Activity): Boolean {
        val d = details ?: return false
        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(d).build()))
            .build()
        return client.launchBillingFlow(activity, params).responseCode == BillingClient.BillingResponseCode.OK
    }

    private fun onUpdated(result: BillingResult, purchases: List<Purchase>?) {
        if (result.responseCode != BillingClient.BillingResponseCode.OK || purchases == null) return
        scope.launch { purchases.forEach { handle(it) } }
    }

    /** 결제는 됐는데 아직 지급/소모되지 않은 것들을 다시 처리 */
    suspend fun refreshPending() {
        if (!client.isReady) return
        val result = client.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build(),
        )
        result.purchasesList.forEach { handle(it) }
    }

    private suspend fun handle(p: Purchase) {
        if (p.purchaseState != Purchase.PurchaseState.PURCHASED) return
        val productId = p.products.firstOrNull() ?: return
        val ok = runCatching { onPurchased(productId, p.purchaseToken) }.getOrDefault(false)
        if (ok) {
            // 서버가 지급을 기록한 뒤에만 소모 → 같은 상품을 다시 살 수 있게 됨
            client.consumePurchase(ConsumeParams.newBuilder().setPurchaseToken(p.purchaseToken).build())
        }
    }
}
