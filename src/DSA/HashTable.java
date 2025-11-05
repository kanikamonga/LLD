package DSA;

import java.util.*;

import static java.util.stream.Collectors.joining;

public class HashTable<K, V> {
	int   initialCapacity = 16;
	float loadFactor      = 0.75f;
	int   currSize        = 0;
	
	private static Object[] arr;
	
	public HashTable() {
		arr = new Object[initialCapacity];
	}
	
	static class Entry<K, V> {
		K k;
		V v;
		
		Entry(K k, V v) {
			this.k = k;
			this.v = v;
		}
		
	}
	
	public V get(K key) {
		Entry<K, V> e = getEntry(key);
		if (e == null) {
			return null;
		}
		return  e.v;
		
	}
	
	public Entry<K, V> getEntry(K key) {
		int    index  = getIndex(key);
		Object object = arr[index];
		if (object == null) {
			return null;
		} else {
			LinkedList<Entry<K, V>> list = (LinkedList<Entry<K, V>>) object;
			for (Entry e : list) {
				if (e.k.equals(key)) {
					return e;
				}
			}
			return null;
		}
	}
	
	public void put(K key, V value) {
		
		int    index  = getIndex(key);
		Object object = arr[index];
		if (object == null) {
			LinkedList<Entry<K, V>> list = new LinkedList<>();
			list.add(new Entry<>(key, value));
			object = list;
			currSize++;
			
		} else {
			LinkedList<Entry<K, V>> list = (LinkedList<Entry<K, V>>) object;
			
			Entry e = getEntry(key);
			if (e == null) {
				list.add(new Entry(key, value));
			} else {
				e.v = value;
			}
		}
		arr[index] = object;
		
		if ((float) currSize / arr.length >= loadFactor) {
			resize();
		}
		
	}
	
	public int getIndex(K key) {
		return key.hashCode() % arr.length;
	}
	
	public void resize() {
		Object[] oldArr = arr;
		arr = new Object[initialCapacity * 2];
		
		int index = 0;
		for (Object o : oldArr) {
			arr[index] = o;
			index++;
		}
	}
	
}
