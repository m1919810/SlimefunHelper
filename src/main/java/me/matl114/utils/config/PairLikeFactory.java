package me.matl114.utils.config;

import com.mojang.datafixers.util.Pair;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import lombok.AllArgsConstructor;

public interface PairLikeFactory<A, B, P> {
    P create(A first, B second);

    A getFirst(P pair);

    B getSecond(P pair);

    default P withFirst(P val, A value2) {
        return create(value2, getSecond(val));
    }

    default P withSecond(P val, B value2) {
        return create(getFirst(val), value2);
    }

    default WrapperFactory<A, P> asFirstWrapper(Supplier<P> source) {
        return WrapperFactory.of(s -> this.withFirst(source.get(), s), this::getFirst);
    }

    default WrapperFactory<B, P> asSecondWrapper(Supplier<P> source) {
        return WrapperFactory.of(s -> this.withSecond(source.get(), s), this::getSecond);
    }

    static <A, B, P> PairLikeFactory<A, B, P> of(
            BiFunction<A, B, P> creator, Function<P, A> firstGetter, Function<P, B> secondGetter) {
        return new BasePairLikeFactory<>(creator, firstGetter, secondGetter);
    }

    // 可选：交换 first 和 second 的角色（要求 P 支持互换，这里返回新工厂，但 P 类型不变，需谨慎使用）
    default PairLikeFactory<B, A, P> swap() {
        return of((b, a) -> create(a, b), this::getSecond, this::getFirst);
    }

    public static final PairLikeFactory<?, ?, Pair<?, ?>> PAIR_FACTORY =
            new BasePairLikeFactory<>(Pair::of, Pair::getFirst, Pair::getSecond);

    public static <A, B> PairLikeFactory<A, B, Pair<A, B>> pair() {
        return (PairLikeFactory) PAIR_FACTORY;
    }

    @AllArgsConstructor
    class BasePairLikeFactory<A, B, P> implements PairLikeFactory<A, B, P> {
        private final BiFunction<A, B, P> creator;
        private final Function<P, A> firstGetter;
        private final Function<P, B> secondGetter;

        @Override
        public P create(A first, B second) {
            return creator.apply(first, second);
        }

        @Override
        public A getFirst(P pair) {
            return firstGetter.apply(pair);
        }

        @Override
        public B getSecond(P pair) {
            return secondGetter.apply(pair);
        }

        @Override
        public PairLikeFactory<B, A, P> swap() {
            return new BasePairLikeFactory<>((b, a) -> creator.apply(a, b), secondGetter, firstGetter);
        }
    }
}
