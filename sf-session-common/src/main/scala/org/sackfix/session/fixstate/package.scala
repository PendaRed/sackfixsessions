package org.sackfix.session

/**Provides all of the state objects and classes which match the ancient Fix Spec document.
  *
  * ==Overview==
  * On entering a new state we can generate a message, and suggest the next state
  * When receiving a message or event we can propose which state to enter next - which will
  * fire off a message and move the state on again.
  *
  * Its a wierd state machine taken from the Fix Spec.  Bad pseudocode below:
  *
  * SomeStateTransiton = handleEvent()
  * moveState(someStateTransition)
  *
  * where
  *
  * moveState(newState) :state
  *   Somemsg = newState.stateTransitionSessionMessage
  *   newState.nextState match{
  *     case None=> newState
  *     case Some(nextState) => moveState(newStata)
  *   }
  */
package object fixstate {

}
