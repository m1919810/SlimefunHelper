package me.matl114.events.impl;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;

@Data
@AllArgsConstructor
@Getter
@Accessors(fluent = true, chain = true)
public class UseItem {
    @Setter
    ActionResult actionResult;

    final Hand hand;
}
