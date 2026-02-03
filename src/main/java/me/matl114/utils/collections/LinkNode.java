package me.matl114.utils.impl.collections;

import it.unimi.dsi.fastutil.BidirectionalIterator;

public class LinkNode<TYPE> {
    public LinkNode(TYPE v){
        this.value = v;
    }
    public LinkNode(TYPE v, LinkNode<TYPE> next){
        this.value = v;
        this.next = next;
        if(next != null){
            next.prev = this;
        }
    }

    public TYPE value;
    public LinkNode<TYPE> prev;
    public LinkNode<TYPE> next;
    public static <TYPE> LinkNode<TYPE> createHead(){
        return new LinkNode<>(null);
    }

    public LinkNode<TYPE> insertAfter(TYPE value){
        this.next = new LinkNode<>(value, this.next);
        this.next.prev = this;
        return this.next;
    }

    public static <TYPE> BidirectionalIterator<TYPE> iterator(final LinkNode<TYPE> curr){
        return iter0(curr, curr.next);
    }
    private static <TYPE> BidirectionalIterator<TYPE> iter0(final LinkNode<TYPE> origin0, final LinkNode<TYPE> curr){
        //我们需要保证curr是head
        //curr.prev == null

        return new BidirectionalIterator<TYPE>() {
            LinkNode<TYPE> lastRet;
            @Override
            public TYPE previous() {
                lastRet = currInternal;
                currInternal = currInternal.prev;
                if(currInternal == null){
                    currInternal = origin0;
                }
                return lastRet.value;
            }

            @Override
            public boolean hasPrevious() {
                return currInternal!= origin0 ;
            }

            LinkNode<TYPE> currInternal;
            {
                currInternal = origin0;
                currInternal.next = curr;
            }
            @Override
            public boolean hasNext() {
                return currInternal.next != null;
            }

            @Override
            public TYPE next() {
                return (lastRet = currInternal = currInternal.next).value;
            }
            public void remove(){
                LinkNode<TYPE> next = lastRet.next;
                if(next != null){
                    //考虑前一个是否是origin0，
                    //由于origin0是独立的 我们不会把他引入任何ret值的prev中
                    next.prev = lastRet.prev;
                }
                LinkNode<TYPE> pre = lastRet.prev == null? origin0 : lastRet.prev;
                if(currInternal == lastRet){
                    //next remove,弹回cursor
                    currInternal = pre;
                }
                pre.next = next;
                lastRet.prev = lastRet.next = null;
                lastRet = null;
            }
        };
    }
}
