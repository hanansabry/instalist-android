package org.noorganization.instalist.controller.event;

import org.noorganization.instalist.model.Unit;

/**
 * Event for notifying about changes of units. Fired by the controller.
 * Created by daMihe on 22.07.2015.
 */
public class UnitChangedMessage {
    public Change mChange;
    public Unit   mUnit;

    public UnitChangedMessage(Change _change, Unit _unit) {
        mChange = _change;
        mUnit   = _unit;
    }
}
