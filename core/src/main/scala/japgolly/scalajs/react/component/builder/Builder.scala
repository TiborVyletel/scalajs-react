package japgolly.scalajs.react.component.builder

import scalajs.js
import japgolly.scalajs.react.{Callback, CallbackTo, Children, CtorType, PropsChildren, raw}
import japgolly.scalajs.react.component._
import japgolly.scalajs.react.internal._
import japgolly.scalajs.react.vdom.VdomElement
import Scala._

object Builder {
  import Lifecycle._

  type InitStateFn [-P, +S]  = Box[P] => Box[S]
  type NewBackendFn[P, S, B] = BackendScope[P, S] => B
  type RenderFn    [P, S, B] = RenderScope[P, S, B] => VdomElement

  type Config[P, C <: Children, S, B] = Step4[P, C, S, B] => Step4[P, C, S, B]

  private val InitStateUnit: InitStateFn[Any, Unit] =
    _ => Box.Unit

  implicit def defaultToNoState  [P](b: Step1[P]): Step2[P, Unit] = b.stateless
  implicit def defaultToNoBackend[X, P, S](b: X)(implicit ev: X => Step2[P, S]): Step3[P, S, Unit] = b.noBackend

  // ===================================================================================================================

  /** You're on step 1/4 on the way to building a component.
    *
    * If your component is to be stateful, here you must specify it's initial state.
    *
    * If your component is to be stateless, you can skip this step or explicitly use `.stateless`.
    */
  final class Step1[P](name: String) {
    // Dealiases type aliases :(
    // type Next[S] = Step2[P, S]

    def initialState[S](s: => S): Step2[P, S] =
      new Step2(name, _ => Box(s))

    def initialStateFromProps[S](f: P => S): Step2[P, S] =
      new Step2(name, p => Box(f(p.unbox)))

    def initialStateCallback[S](cb: CallbackTo[S]): Step2[P, S] =
      initialState(cb.runNow())

    def initialStateCallbackFromProps[S](f: P => CallbackTo[S]): Step2[P, S] =
      initialStateFromProps(f(_).runNow())

    def stateless: Step2[P, Unit] =
      new Step2(name, InitStateUnit)
  }

  // ===================================================================================================================

  /** You're on step 2/4 on the way to building a component.
    *
    * Here you specify whether your component has a "backend" or not.
    * A backend like a class that is created when your component mounts and remains until it is unmounted.
    *
    * If you don't need a backend, you can skip this step or explicitly use `.noBackend`.
    *
    * Making common cases convenient, you can even use `.renderBackend` or `.renderBackendWithChildren` to take care
    * of both this and the following step automatically, using macros to save you typing boring boilerplate.
    * If you have an unhealthy fear of macros you can ignore then and do it all manually too; the macros don't have any
    * special privileges.
    */
  final class Step2[P, S](name: String, initStateFn: InitStateFn[P, S]) {
    // Dealiases type aliases :(
    // type Next[B] = Step3[P, S, B]

    def backend[B](f: NewBackendFn[P, S, B]): Step3[P, S, B] =
      new Step3(name, initStateFn, f)

    def noBackend: Step3[P, S, Unit] =
      backend(_ => ())

    /**
     * Shortcut for:
     *
     * {{{
     *   .backend[B](new B(_))
     *   .renderBackend
     * }}}
     */
    def renderBackend[B]: Step4[P, Children.None, S, B] =
      macro ComponentBuilderMacros.backendAndRender[P, S, B]

    /**
     * Shortcut for:
     *
     * {{{
     *   .backend[B](new B(_))
     *   .renderBackendWithChildren
     * }}}
     */
    def renderBackendWithChildren[B]: Step4[P, Children.Varargs, S, B] =
      macro ComponentBuilderMacros.backendAndRenderWithChildren[P, S, B]
  }

  // ===================================================================================================================

  /** You're on step 3/4 on the way to building a component.
    *
    * Here you specify your component's render function. For convenience there are a plethoria of methods you can call
    * that all do the same thing but accept the argument in different shapes; the suffixes in method names refer to the
    * argument type(s). This means you choose the method that provides the types that you need, rather than having to
    * manually specify the types for all arguments including stuff you don't need.
    *
    * If you're using a backend, then it's highly recommended that you put your render function in the backend.
    * When you do that, make sure it's called `.render` and then here in the builder, use the `.renderBackend` or
    * `.renderBackendWithChildren` methods which will use a macro to inspect your backend's render method and provide
    * everything it needs automatically.
    */
  final class Step3[P, S, B](name: String, initStateFn: InitStateFn[P, S], backendFn: NewBackendFn[P, S, B]) {
    // Dealiases type aliases :(
    // type Next[C <: Children] = Step4[P, C, S, B]

    type $ = RenderScope[P, S, B]

    def renderWith[C <: Children](r: RenderFn[P, S, B]): Step4[P, C, S, B] =
      new Step4[P, C, S, B](name, initStateFn, backendFn, r, Lifecycle.empty)

    // No args

    def renderStatic(r: VdomElement): Step4[P, Children.None, S, B] =
      renderWith(_ => r)

    def render_(r: => VdomElement): Step4[P, Children.None, S, B] =
      renderWith(_ => r)

    // No children

    def render(r: RenderFn[P, S, B]): Step4[P, Children.None, S, B] =
      renderWith(r)

    def renderPS(r: ($, P, S) => VdomElement): Step4[P, Children.None, S, B] =
       renderWith($ => r($, $.props, $.state))

     def renderP(r: ($, P) => VdomElement): Step4[P, Children.None, S, B] =
       renderWith($ => r($, $.props))

     def renderS(r: ($, S) => VdomElement): Step4[P, Children.None, S, B] =
       renderWith($ => r($, $.state))

     def render_PS(r: (P, S) => VdomElement): Step4[P, Children.None, S, B] =
       renderWith($ => r($.props, $.state))

     def render_P(r: P => VdomElement): Step4[P, Children.None, S, B] =
       renderWith($ => r($.props))

     def render_S(r: S => VdomElement): Step4[P, Children.None, S, B] =
       renderWith($ => r($.state))

    // Has children

     def renderPCS(r: ($, P, PropsChildren, S) => VdomElement): Step4[P, Children.Varargs, S, B] =
       renderWith($ => r($, $.props, $.propsChildren, $.state))

     def renderPC(r: ($, P, PropsChildren) => VdomElement): Step4[P, Children.Varargs, S, B] =
       renderWith($ => r($, $.props, $.propsChildren))

     def renderCS(r: ($, PropsChildren, S) => VdomElement): Step4[P, Children.Varargs, S, B] =
       renderWith($ => r($, $.propsChildren, $.state))

     def renderC(r: ($, PropsChildren) => VdomElement): Step4[P, Children.Varargs, S, B] =
       renderWith($ => r($, $.propsChildren))

     def render_PCS(r: (P, PropsChildren, S) => VdomElement): Step4[P, Children.Varargs, S, B] =
       renderWith($ => r($.props, $.propsChildren, $.state))

     def render_PC(r: (P, PropsChildren) => VdomElement): Step4[P, Children.Varargs, S, B] =
       renderWith($ => r($.props, $.propsChildren))

     def render_CS(r: (PropsChildren, S) => VdomElement): Step4[P, Children.Varargs, S, B] =
       renderWith($ => r($.propsChildren, $.state))

     def render_C(r: PropsChildren => VdomElement): Step4[P, Children.Varargs, S, B] =
       renderWith($ => r($.propsChildren))

    /**
     * Use a method named `render` in the backend, automatically populating its arguments with props and state
     * where needed.
     */
    def renderBackend: Step4[P, Children.None, S, B] =
      macro ComponentBuilderMacros.renderBackend[P, S, B]

    /**
     * Use a method named `render` in the backend, automatically populating its arguments with props, state, and
     * propsChildren where needed.
     */
    def renderBackendWithChildren: Step4[P, Children.Varargs, S, B] =
      macro ComponentBuilderMacros.renderBackendWithChildren[P, S, B]
  }

  // ===================================================================================================================

  /** You're on the final step on the way to building a component.
    *
    * This is where you add lifecycle callbacks if you need to.
    *
    * When you're done, simply call `.build` to create and return your `ScalaComponent`.
    */
  final class Step4[P, C <: Children, S, B](val name   : String,
                                            initStateFn: InitStateFn[P, S],
                                            backendFn  : NewBackendFn[P, S, B],
                                            renderFn   : RenderFn[P, S, B],
                                            lifecycle  : Lifecycle[P, S, B]) {
    type This = Step4[P, C, S, B]

    private def copy(name       : String                = this.name,
                     initStateFn: InitStateFn [P, S]    = this.initStateFn,
                     backendFn  : NewBackendFn[P, S, B] = this.backendFn,
                     renderFn   : RenderFn    [P, S, B] = this.renderFn,
                     lifecycle  : Lifecycle   [P, S, B] = this.lifecycle  ): This =
      new Step4(name, initStateFn, backendFn, renderFn, lifecycle)

    private def lcAppend[I, O](lens: Lens[Lifecycle[P, S, B], Option[I => O]])(g: I => O)(implicit s: Semigroup[O]): This =
      copy(lifecycle = lifecycle.append(lens)(g)(s))

    def configure(fs: Config[P, C, S, B]*): This =
      fs.foldLeft(this)((s, f) => f(s))

    /**
     * Invoked once, only on the client (not on the server), immediately after the initial rendering occurs. At this point
     * in the lifecycle, the component has a DOM representation which you can access via `ReactDOM.findDOMNode(this)`.
     * The `componentDidMount()` method of child components is invoked before that of parent components.
     *
     * If you want to integrate with other JavaScript frameworks, set timers using `setTimeout` or `setInterval`, or send
     * AJAX requests, perform those operations in this method.
     */
    def componentDidMount(f: ComponentDidMountFn[P, S, B]): This =
      lcAppend(Lifecycle.componentDidMount)(f)

    /**
     * Invoked immediately after the component's updates are flushed to the DOM. This method is not called for the initial
     * render.
     *
     * Use this as an opportunity to operate on the DOM when the component has been updated.
     */
    def componentDidUpdate(f: ComponentDidUpdateFn[P, S, B]): This =
      lcAppend(Lifecycle.componentDidUpdate)(f)

    /**
     * Invoked once, both on the client and server, immediately before the initial rendering occurs.
     * If you call `setState` within this method, `render()` will see the updated state and will be executed only once
     * despite the state change.
     */
    def componentWillMount(f: ComponentWillMountFn[P, S, B]): This =
      lcAppend(Lifecycle.componentWillMount)(f)

    /**
     * Invoked when a component is receiving new props. This method is not called for the initial render.
     *
     * Use this as an opportunity to react to a prop transition before `render()` is called by updating the state using
     * `this.setState()`. The old props can be accessed via `this.props`. Calling `this.setState()` within this function
     * will not trigger an additional render.
     *
     * Note: There is no analogous method `componentWillReceiveState`. An incoming prop transition may cause a state
     * change, but the opposite is not true. If you need to perform operations in response to a state change, use
     * `componentWillUpdate`.
     */
    def componentWillReceiveProps(f: ComponentWillReceivePropsFn[P, S, B]): This =
      lcAppend(Lifecycle.componentWillReceiveProps)(f)

    /**
     * Invoked immediately before a component is unmounted from the DOM.
     *
     * Perform any necessary cleanup in this method, such as invalidating timers or cleaning up any DOM elements that were
     * created in `componentDidMount`.
     */
    def componentWillUnmount(f: ComponentWillUnmountFn[P, S, B]): This =
      lcAppend(Lifecycle.componentWillUnmount)(f)

    /**
     * Invoked immediately before rendering when new props or state are being received. This method is not called for the
     * initial render.
     *
     * Use this as an opportunity to perform preparation before an update occurs.
     *
     * Note: You *cannot* use `this.setState()` in this method. If you need to update state in response to a prop change,
     * use `componentWillReceiveProps` instead.
     */
    def componentWillUpdate(f: ComponentWillUpdateFn[P, S, B]): This =
      lcAppend(Lifecycle.componentWillUpdate)(f)

    /**
     * Invoked before rendering when new props or state are being received. This method is not called for the initial
     * render or when `forceUpdate` is used.
     *
     * Use this as an opportunity to `return false` when you're certain that the transition to the new props and state
     * will not require a component update.
     *
     * If `shouldComponentUpdate` returns false, then `render()` will be completely skipped until the next state change.
     * In addition, `componentWillUpdate` and `componentDidUpdate` will not be called.
     *
     * By default, `shouldComponentUpdate` always returns `true` to prevent subtle bugs when `state` is mutated in place,
     * but if you are careful to always treat `state` as immutable and to read only from `props` and `state` in `render()`
     * then you can override `shouldComponentUpdate` with an implementation that compares the old props and state to their
     * replacements.
     *
     * If performance is a bottleneck, especially with dozens or hundreds of components, use `shouldComponentUpdate` to
     * speed up your app.
     */
    def shouldComponentUpdate(f: ShouldComponentUpdateFn[P, S, B]): This =
      lcAppend(Lifecycle.shouldComponentUpdate)(f)(Semigroup.eitherCB)

    /**
     * Invoked before rendering when new props or state are being received. This method is not called for the initial
     * render or when `forceUpdate` is used.
     *
     * Use this as an opportunity to `return false` when you're certain that the transition to the new props and state
     * will not require a component update.
     *
     * If `shouldComponentUpdate` returns false, then `render()` will be completely skipped until the next state change.
     * In addition, `componentWillUpdate` and `componentDidUpdate` will not be called.
     *
     * By default, `shouldComponentUpdate` always returns `true` to prevent subtle bugs when `state` is mutated in place,
     * but if you are careful to always treat `state` as immutable and to read only from `props` and `state` in `render()`
     * then you can override `shouldComponentUpdate` with an implementation that compares the old props and state to their
     * replacements.
     *
     * If performance is a bottleneck, especially with dozens or hundreds of components, use `shouldComponentUpdate` to
     * speed up your app.
     */
    def shouldComponentUpdatePure(f: ShouldComponentUpdate[P, S, B] => Boolean): This =
      shouldComponentUpdate($ => CallbackTo(f($)))

    @deprecated("Use componentDidMountConst",         "1.0.0") def componentDidMountCB        (cb: Callback           ): This = componentDidMount        (_ => cb)
    @deprecated("Use componentDidUpdateConst",        "1.0.0") def componentDidUpdateCB       (cb: Callback           ): This = componentDidUpdate       (_ => cb)
    @deprecated("Use componentWillMountConst",        "1.0.0") def componentWillMountCB       (cb: Callback           ): This = componentWillMount       (_ => cb)
    @deprecated("Use componentWillReceivePropsConst", "1.0.0") def componentWillReceivePropsCB(cb: Callback           ): This = componentWillReceiveProps(_ => cb)
    @deprecated("Use componentWillUnmountConst",      "1.0.0") def componentWillUnmountCB     (cb: Callback           ): This = componentWillUnmount     (_ => cb)
    @deprecated("Use componentWillUpdateConst",       "1.0.0") def componentWillUpdateCB      (cb: Callback           ): This = componentWillUpdate      (_ => cb)
    @deprecated("Use shouldComponentUpdateConst",     "1.0.0") def shouldComponentUpdateCB    (cb: CallbackTo[Boolean]): This = shouldComponentUpdate    (_ => cb)

    def componentDidMountConst        (cb: Callback           ): This = componentDidMount         (_ => cb)
    def componentDidUpdateConst       (cb: Callback           ): This = componentDidUpdate        (_ => cb)
    def componentWillMountConst       (cb: Callback           ): This = componentWillMount        (_ => cb)
    def componentWillReceivePropsConst(cb: Callback           ): This = componentWillReceiveProps (_ => cb)
    def componentWillUnmountConst     (cb: Callback           ): This = componentWillUnmount      (_ => cb)
    def componentWillUpdateConst      (cb: Callback           ): This = componentWillUpdate       (_ => cb)
    def shouldComponentUpdateConst    (cb: CallbackTo[Boolean]): This = shouldComponentUpdate     (_ => cb)
    def shouldComponentUpdateConst    (b : Boolean            ): This = shouldComponentUpdateConst(CallbackTo pure b)

    def spec: raw.ReactComponentSpec[Box[P], Box[S]] = {
      type RawComp = raw.ReactComponent[Box[P], Box[S]]
      val spec = js.Object().asInstanceOf[raw.ReactComponentSpec[Box[P], Box[S]]]

      @inline def castV($: RawComp) = $.asInstanceOf[RawMounted[P, S, B]]

      for (n <- Option(name))
        spec.displayName = n

      def withMounted[A](f: RenderScope[P, S, B] => A): js.ThisFunction0[RawComp, A] =
        ($: RawComp) =>
          f(new RenderScope(castV($)))

      spec.render = withMounted(renderFn.andThen(_.rawElement))

      spec.getInitialState =
        (($: js.Dynamic) => initStateFn($.props.asInstanceOf[Box[P]])): js.ThisFunction0[js.Dynamic, Box[S]]

      val setup: RawComp => Unit =
        $ => {
          val jMounted : JsMounted    [P, S, B] = Js.mounted[Box[P], Box[S]]($).addFacade[Vars[P, S, B]]
          val sMountedI: MountedImpure[P, S, B] = Scala.mountedRoot(jMounted)
          val sMountedP: MountedPure  [P, S, B] = sMountedI.withEffect
          val backend  : B                      = backendFn(sMountedP)
          jMounted.raw.mountedImpure = sMountedI
          jMounted.raw.mountedPure   = sMountedP
          jMounted.raw.backend       = backend
        }
      spec.componentWillMount = lifecycle.componentWillMount match {
        case None    => setup
        case Some(f) =>
          ($: RawComp) => {
            setup($)
            f(new ComponentWillMount(castV($))).runNow()
          }
      }

      val teardown: RawComp => Unit =
        $ => {
          val vars = castV($)
          vars.mountedImpure = null
          vars.mountedPure   = null
          vars.backend       = null.asInstanceOf[B]
        }
      spec.componentWillUnmount = lifecycle.componentWillUnmount match {
        case None    => teardown
        case Some(f) =>
          ($: RawComp) => {
            f(new ComponentWillUnmount(castV($))).runNow()
            teardown($)
          }
      }

      lifecycle.componentDidMount.foreach(f =>
        spec.componentDidMount = ($: RawComp) =>
          f(new ComponentDidMount(castV($))).runNow())

      lifecycle.componentDidUpdate.foreach(f =>
        spec.componentDidUpdate = ($: RawComp, p: Box[P], s: Box[S]) =>
          f(new ComponentDidUpdate(castV($), p.unbox, s.unbox)).runNow())

      lifecycle.componentWillReceiveProps.foreach(f =>
        spec.componentWillReceiveProps = ($: RawComp, p: Box[P]) =>
          f(new ComponentWillReceiveProps(castV($), p.unbox)).runNow())

      lifecycle.componentWillUpdate.foreach(f =>
        spec.componentWillUpdate = ($: RawComp, p: Box[P], s: Box[S]) =>
          f(new ComponentWillUpdate(castV($), p.unbox, s.unbox)).runNow())

      lifecycle.shouldComponentUpdate.foreach(f =>
        spec.shouldComponentUpdate = ($: RawComp, p: Box[P], s: Box[S]) =>
          f(new ShouldComponentUpdate(castV($), p.unbox, s.unbox)).runNow())

//        if (jsMixins.nonEmpty)
//          spec("mixins") = JArray(jsMixins: _*)
//
//        lc.configureSpec.foreach(_(spec2).runNow())

      spec
    }

    /** This is the end of the road for this component builder.
      *
      * @return Your brand-new, spanking, ScalaComponent. Mmmmmmmm, new-car smell.
      */
    def build(implicit ctorType: CtorType.Summoner[Box[P], C]): Scala.Component[P, S, B, ctorType.CT] = {
      val rc = raw.React.createClass(spec)
      Js.component[Box[P], C, Box[S]](rc)(ctorType)
        .addFacade[Vars[P, S, B]]
        .cmapCtorProps[P](Box(_))
        .mapUnmounted(_
          .mapUnmountedProps(_.unbox)
          .mapMounted(Scala.mountedRoot))
    }
  }
}