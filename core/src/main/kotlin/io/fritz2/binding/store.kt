package io.fritz2.binding

import io.fritz2.flow.asSharedFlow
import io.fritz2.optics.Lens
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.channels.BroadcastChannel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * defines a type for transforming one value into the next
 */
typealias Update<T> = (T) -> T


/**
 * A [Handler] defines, how to handle actions in your [Store]. Each Handler accepts actions of a defined type.
 * If your handler just needs the current value of the [Store] and no action, use [Unit].
 *
 * @param execute defines how to handle the values of the connected [Flow]
 */
open class Handler<A>(inline val execute: (Flow<A>) -> Unit) {
    /**
     * you can bind a [Flow] of actions/events to this [Handler] by using <= as an operator.
     */
    // syntactical sugar to write handler <= event-flow
    operator fun compareTo(flow: Flow<A>): Int {
        execute(flow)
        return 0
    }
}

/**
 * An [EmittingHandler] is a special [Handler] that is a new [Flow] by itself. You can emit values to this [Flow] from your code
 * and connect it to other [Handler]s on this or on other [Store]s. This way inter-store-communication is done in fritz2.
 *
 * @param bufferSize number of values of the new [Flow] to buffer
 * @param execute defines how to handle the values of the connected [Flow]
 */
class EmittingHandler<A, E>(bufferSize: Int, inline val execute: (Flow<A>, SendChannel<E>) -> Unit) : Flow<E> {

    internal val channel = BroadcastChannel<E>(bufferSize)

    @InternalCoroutinesApi
    /**
     * implementing the [Flow]-interface
     */
    override suspend fun collect(collector: FlowCollector<E>) {
        collector.emitAll(channel.asFlow())
    }

    /**
     * you can bind a [Flow] of actions/events to this [Handler] by using <= as an operator.
     */
    // syntactical sugar to write handler <= event-flow
    operator fun compareTo(flow: Flow<A>): Int {
        execute(flow, channel)
        return 0
    }
}


//FIXME: we need an Applicator, that can access the actual model
class Applicator<A, X>(inline val execute: suspend (A) -> Flow<X>) {
    infix fun andThen(nextHandler: Handler<X>) = Handler<A> {
        nextHandler.execute(it.flatMapConcat(this.execute))
    }

    infix fun <Y> andThen(nextApplicator: Applicator<X, Y>): Applicator<A, Y> = Applicator {
        execute(it).flatMapConcat(nextApplicator.execute)
    }
}


/**
 * The [Store] is the main type for all data binding activities. It the base class of all concrete Stores like [RootStore], [SubStore], etc.
 */
abstract class Store<T> : CoroutineScope by MainScope() {

    /**
     * factory method to create a [Handler] mapping the actual value of the [Store] and a given Action to a new value.
     *
     *
     * @param execute lambda that is executed whenever a new action-value appears on the connected event-[Flow].
     */
    inline fun <A> handle(crossinline execute: (T, A) -> T) = Handler<A> {
        launch {
            it.collect {
                enqueue { t -> execute(t, it) }
            }
        }
    }

    /**
     * factory method to create a [Handler] that does not take an Action
     *
     * @param execute lambda that is execute for each event on the connected [Flow]
     */
    inline fun handle(crossinline execute: (T) -> T) = Handler<Unit> {
        launch {
            it.collect {
                enqueue { t -> execute(t) }
            }
        }
    }

    /**
     * factory method to create a [EmittingHandler] taking an action-value and the current store value to derive the new value.
     * An [EmittingHandler] is a [Flow] by itself and can therefore be connected to other [Handler]s even in other [Store]s.
     *
     * @param bufferSize number of values to buffer
     * @param execute lambda that is executed for each action-value on the connected [Flow]. You can emit values from this lambda.
     */
    //FIXME: why no suspend on execute
    inline fun <A, E> handleAndEmit(bufferSize: Int = 1, crossinline execute: SendChannel<E>.(T, A) -> T) =
        EmittingHandler<A, E>(bufferSize) { inFlow, outChannel ->
            launch {
                inFlow.collect {
                    enqueue { t -> outChannel.execute(t, it) }
                }
            }
        }

    /**
     * factory method to create an [EmittingHandler] that does not take an action in it's [execute]-lambda.
     *
     * @param bufferSize number of values to buffer
     * @param execute lambda that is executed for each event on the connected [Flow]. You can emit values from this lambda.
     */
    inline fun <A, E> handleAndEmit(bufferSize: Int = 1, crossinline execute: SendChannel<E>.(T) -> T) =
        EmittingHandler<Unit, E>(bufferSize) { inFlow, outChannel ->
            launch {
                inFlow.collect {
                    enqueue { t -> outChannel.execute(t) }
                }
            }
        }

    /**
     * factory method, to create an [Applicator].
     *
     * @param mapper defines how to transform the given action into a new asynchronous [Flow], for example by calling a remote interface.
     */
    fun <A, X> apply(mapper: suspend (A) -> Flow<X>) = Applicator(mapper)


    /**
     * abstract method defining, how this [Store] handles an [Update]
     *
     * @param update the [Update] to handle
     */
    abstract suspend fun enqueue(update: Update<T>)

    /**
     * base-id of this [Store]. ids of depending [Store]s are concatenated separated by a dot.
     */
    abstract val id: String


    /**
     * the [Flow] representing the current value of the [Store]. Use this to bind it to ui-elements or derive calculated values by using [map] for example.
     */
    abstract val data: Flow<T>

    /**
     * a simple [Handler] that just takes the given action-value as the new value for the [Store].
     */
    val update = handle<T> { _, newValue -> newValue }
}

/**
 * A [Store] can be initialized with a given value. Use a [RootStore] to "store" your model and create [SubStore]s from here.
 *
 * @param initialData: the first current value of this [Store]
 * @param id: the id of this store. ids of [SubStore]s will be concatenated.
 * @param bufferSize: number of values to buffer
 */
open class RootStore<T>(initialData: T, override val id: String = "", bufferSize: Int = 1) : Store<T>() {

    //TODO: best capacity?
    private val updates = BroadcastChannel<Update<T>>(bufferSize)
    private val applyUpdate: suspend (T, Update<T>) -> T = { lastValue, update -> update(lastValue) }

    /**
     * in a [RootStore] an [Update] is handled by sending it to the internal [updates]-channel.
     */
    override suspend fun enqueue(update: Update<T>) {
        updates.send(update)
    }

    /**
     * the current value of a [RootStore] is derived be applying the updates on the internal channel one by one to get the next value.
     * the [Flow] only emit's a new value, when the value is differs from the last one to avoid calculations and updates that are not necessary.
     * This has to be a SharedFlow, because the updated should only be applied once, regardless how many depending values or ui-elements or bound to it.
     */
    override val data = updates.asFlow().scan(initialData, applyUpdate).distinctUntilChanged().asSharedFlow()

    /**
     * create a [SubStore] that represents a certain part of your data model.
     *
     * @param lens: a [Lens] describing, which part of your data model you will create [SubStore] for. Use @Lenses to let your compiler
     * create the lenses for you or use the buildLens-factory-method.
     */
    fun <X> sub(lens: Lens<T, X>) = SubStore(this, lens, this, lens)
}


/**
 * represents the id of certain element in a deep nested model structure.
 *
 * @property id [String] representation of the id
 */
interface ModelId<T> {
    val id: String

    /**
     * method to create a [ModelId] for a part of your data-model
     *
     * @param lens a [Lens] describing, of which part of your data model you want the id
     */
    fun <X> sub(lens: Lens<T, X>): ModelId<X>
}


/**
 * Starting point for creating your [ModelId]s. Same as [RootStore] but just for ids. Use it in validation for example.
 */
class ModelIdRoot<T>(override val id: String = "") : ModelId<T> {

    /**
     * method to create a [ModelId] for a part of your data-model
     *
     * @param lens a [Lens] describing, of which part of your data model you want the id
     */
    override fun <X> sub(lens: Lens<T, X>): ModelId<X> = ModelIdSub(this, lens, this, lens)
}


/**
 * Same as [SubStore] but just for ids. Use it in validation for example.
 */
class ModelIdSub<R, P, T>(
    private val parent: ModelId<P>,
    private val lens: Lens<P, T>,
    val rootStore: ModelIdRoot<R>,
    val rootLens: Lens<R, T>
): ModelId<T> {
    /**
     * defines how the id of a part is derived from the one of it's parent
     */
    override val id: String by lazy { "${parent.id}.${lens._id}" }

    /**
     * method to create a [ModelId] for a part of your data-model
     *
     * @param lens a [Lens] describing, of which part of your data model you want the id
     */
    override fun <X> sub(lens: Lens<T, X>): ModelIdSub<R, T, X> =
        ModelIdSub(this, lens, rootStore, this.rootLens + lens)
}