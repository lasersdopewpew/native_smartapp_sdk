package ru.sberdevices.camera.controller

import android.content.Context
import android.hardware.camera2.CameraManager
import android.os.Handler
import android.os.HandlerThread
import android.view.WindowManager
import androidx.annotation.AnyThread
import ru.sberdevices.camera.factories.ImageReaderFactoryImpl
import ru.sberdevices.camera.factories.camera.CameraFactory
import ru.sberdevices.camera.factories.camera.CameraOpenerImpl
import ru.sberdevices.camera.factories.camera.ConnectionCallback
import ru.sberdevices.camera.factories.preview.PreviewCallbackFactoryImpl
import ru.sberdevices.camera.factories.session.CameraSessionFactory
import ru.sberdevices.camera.factories.session.SessionOpenerImpl
import ru.sberdevices.camera.factories.snapshot.SnapshotCallbackFactoryImpl
import ru.sberdevices.camera.statemachine.ActionDispatcherImpl
import ru.sberdevices.camera.statemachine.CameraStateMachineImpl
import ru.sberdevices.camera.statemachine.StateHolderImpl
import ru.sberdevices.camera.utils.CameraCoveredListenerImpl
import ru.sberdevices.camera.utils.CameraCoveredReceiverImpl
import ru.sberdevices.camera.utils.CameraExceptionHandler
import ru.sberdevices.camera.utils.CameraExceptionHandlerImpl
import ru.sberdevices.common.logger.Logger

object CameraStarterFactory {
    private val logger = Logger.get("CameraStarterFactory")

    @JvmStatic
    @AnyThread
    fun create(
        context: Context,
        exceptionHandler: CameraExceptionHandler? = null
    ): CameraController {
        logger.debug { "create" }
        val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val cameraHandler = run {
            val handlerThread = HandlerThread("camera-thread")
            handlerThread.start()
            Handler(handlerThread.looper)
        }
        val cameraExceptionHandler = exceptionHandler ?: run {
            CameraExceptionHandlerImpl()
        }
        val stateHolder = StateHolderImpl()
        val actionDispatcher = ActionDispatcherImpl(stateHolder)
        val cameraFactory = CameraFactory(
            cameraManager,
            windowManager,
            cameraExceptionHandler
        )
        val cameraOpener = CameraOpenerImpl(
            cameraManager,
            ConnectionCallback(
                actionDispatcher,
                cameraFactory
            ),
            cameraHandler
        )
        val sessionFactory = CameraSessionFactory(cameraExceptionHandler)
        val sessionOpener = SessionOpenerImpl(
            sessionFactory,
            actionDispatcher,
            cameraHandler
        )
        val previewCallFactory = PreviewCallbackFactoryImpl(actionDispatcher)
        val snapshotCallbackFactory = SnapshotCallbackFactoryImpl()
        val imageReaderFactory = ImageReaderFactoryImpl(windowManager)
        val stateMachine = CameraStateMachineImpl(
            stateHolder,
            actionDispatcher,
            cameraOpener,
            sessionOpener,
            previewCallFactory,
            snapshotCallbackFactory,
            imageReaderFactory,
            cameraHandler,
            cameraExceptionHandler,
        )
        val coveredReceiver = CameraCoveredReceiverImpl(
            context,
            CameraCoveredListenerImpl(
                actionDispatcher,
                cameraHandler
            )
        )
        stateHolder.init(stateMachine)
        return CameraControllerImpl(
            stateMachine,
            coveredReceiver,
            cameraHandler
        )
    }
}
