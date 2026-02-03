package me.matl114.utils.codecs;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;

import java.util.function.Predicate;

public class NullCodec<A> implements Codec<A> {
    Codec<A> delegate;
    Predicate<A> predicate;
    A empty;
    public NullCodec(Codec<A> delegate, Predicate<A> predicate, A empty) {
        this.delegate = delegate;
        this.predicate = predicate;
        this.empty = empty;
    }


    @Override
    public <T> DataResult<T> encode(A input, DynamicOps<T> ops, T prefix) {
        if(this.predicate.test(input)){
            return DataResult.success(ops.empty());
        }
        return delegate.encode(input, ops, prefix);
    }

    @Override
    public <T> DataResult<Pair<A, T>> decode(DynamicOps<T> ops, T input) {
        if(ops.empty() == input || input == null){
            return DataResult.success(Pair.of(empty, input));
        }else return delegate.decode(ops, input);
    }


}
