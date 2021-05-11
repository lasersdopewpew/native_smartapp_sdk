package ru.sberdevices.pub.demoapp.ui.smartapp

import androidx.annotation.VisibleForTesting
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import ru.sberdevices.common.logger.Logger
import ru.sberdevices.messaging.MessageId
import ru.sberdevices.messaging.MessageName
import ru.sberdevices.messaging.Messaging
import ru.sberdevices.messaging.Payload
import ru.sberdevices.services.appstate.AppStateHolder

/**
 * In this example view model gets messages from smartapp backend by [Messaging].
 * Also it shares it's state with smartapp backend via [AppStateHolder].
 */
class SmartAppViewModel(
    private val messaging: Messaging,
    private val appStateHolder: AppStateHolder,
) : ViewModel() {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val commandParser = Json {
        classDiscriminator = "command"
        ignoreUnknownKeys = true
    }
    private val logger by Logger.lazy("SmartAppViewModel")
    private val currentClothes: MutableSet<String> = HashSet()

    private val _clothes = MutableLiveData<Clothes>()
    private val _buyItems = MutableLiveData<BuyItems>()

    val clothes: LiveData<Clothes> = _clothes
    val buyItems: LiveData<BuyItems> = _buyItems

    private val listener = object : Messaging.Listener {
        override fun onMessage(messageId: MessageId, payload: Payload) {
            logger.debug { "Message ${messageId.value} received: ${payload.data}" }

            val model = commandParser.decodeFromString<BaseCommand>(payload.data)

            when (model) {
                is WearThisCommand -> {
                    model.clothes?.let {
                        currentClothes.add(it.clothes)
                        _clothes.postValue(model.clothes)
                    }
                }
                is BuySuccessCommand -> {
                    if (true == model.buyItems?.contains(BuyItems.ELEPHANT)) {
                        _buyItems.postValue(model.buyItems.last())
                    }
                }
            }

            // send current state to smartapp backend
            appStateHolder.setState(Json.encodeToString(MyAppState("На андроиде ${currentClothes.joinToString()}")))
        }

        override fun onError(messageId: MessageId, throwable: Throwable) {
            logger.error { throwable.stackTraceToString() }
        }
    }

    init {
        messaging.addListener(listener)
    }

    fun sendServerAction() {
        scope.launch {
            messaging.sendAction(
                MessageName.SERVER_ACTION,
                Payload(Json.encodeToString(
                    ServerAction(
                        actionId = "ACTION_FROM_NATIVE_APP",
                        parameters = mapOf(
                            "myParameter" to "Андроид"
                        )
                    )
                ))
            )
        }
    }

    @VisibleForTesting
    public override fun onCleared() {
        messaging.removeListener(listener)
        appStateHolder.setState(null)
    }

}


