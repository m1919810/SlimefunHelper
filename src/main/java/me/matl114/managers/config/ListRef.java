package me.matl114.managers.config;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import lombok.Getter;
import lombok.val;
import me.matl114.utils.config.AttrKeyValue;

public class ListRef extends ObjectRef<List<String>> {
    public static final Class<List<String>> TYPE = (Class<List<String>>) (Class) List.class;

    @Getter
    public List<Predicate<String>> elementValidator = new ArrayList<>();

    public ListRef(List<String> object) {
        super(object);
        addValidator(this::validateInternal);
    }

    public boolean validateElement(String val) {
        for (Predicate<String> validator : elementValidator) {
            if (!validator.test(val)) {
                return false;
            }
        }
        return true;
    }

    public void addElementValidator(Predicate<String> validator) {
        elementValidator.add(validator);
    }

    public void removeElementValidator(Predicate<Predicate<String>> validator) {
        elementValidator.removeIf(validator);
    }

    private boolean validateInternal(List<String> v) {
        return v.stream().allMatch(this::validateElement);
    }

    @Override
    public Object getAsPrimitive() {
        return get().stream().map(Object::toString).toList();
    }

    @Override
    public <W> boolean isSameTypeWith(Ref<W> ref) {
        return ref instanceof ListRef;
    }

    @Override
    public <W> boolean copyValueFrom(Ref<W> otherRef) {
        if (otherRef instanceof ListRef listRef) {
            set(new ArrayList<>(listRef.get()));
            return true;
        }
        return false;
    }

    @Override
    public AttrKeyValue<List<String>> _createKeyValue0(String key) {
        return AttrKeyValue.list(key, this.get());
    }

    @Override
    protected List<String> validateAndCast(Object val) {
        return (List<String>) val;
    }
}
