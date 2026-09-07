package DSA.Amazon;

import java.util.Arrays;

/*
 * Problem:
 * Count pairs (i, j) such that:
 *
 *   i < j and arr[i] > arr[j]
 *
 * Approach:
 * Use merge sort. During the merge of two sorted halves:
 *
 * - If left[leftIndex] <= right[rightIndex], no inversion is created.
 * - Otherwise, right[rightIndex] is smaller than every remaining element in
 *   the left half. Therefore, all remaining left elements form inversions with
 *   right[rightIndex].
 *
 * Equal values are not inversions, so the comparison uses <=.
 *
 * Time Complexity: O(n log n)
 * Space Complexity: O(n)
 */
public class CountInversions {

    public static long countInversions(int[] arr) {
        if (arr == null) {
            throw new IllegalArgumentException("Array cannot be null");
        }
        if (arr.length < 2) {
            return 0;
        }

        int[] temporary = new int[arr.length];
        return mergeSortAndCount(arr, temporary, 0, arr.length - 1);
    }

    private static long mergeSortAndCount(
            int[] arr, int[] temporary, int left, int right) {
        if (left >= right) {
            return 0;
        }

        int middle = left + (right - left) / 2;
        long inversions = 0;
        inversions += mergeSortAndCount(arr, temporary, left, middle);
        inversions += mergeSortAndCount(arr, temporary, middle + 1, right);
        inversions += mergeAndCount(arr, temporary, left, middle, right);
        return inversions;
    }

    private static long mergeAndCount(
            int[] arr, int[] temporary, int left, int middle, int right) {
        int leftIndex = left;
        int rightIndex = middle + 1;
        int mergedIndex = left;
        long inversions = 0;

        while (leftIndex <= middle && rightIndex <= right) {
            if (arr[leftIndex] <= arr[rightIndex]) {
                temporary[mergedIndex++] = arr[leftIndex++];
            } else {
                temporary[mergedIndex++] = arr[rightIndex++];

                // All remaining values in the sorted left half are greater
                // than the selected right value.
                inversions += middle - leftIndex + 1L;
            }
        }

        while (leftIndex <= middle) {
            temporary[mergedIndex++] = arr[leftIndex++];
        }
        while (rightIndex <= right) {
            temporary[mergedIndex++] = arr[rightIndex++];
        }

        for (int index = left; index <= right; index++) {
            arr[index] = temporary[index];
        }
        return inversions;
    }

    public static void main(String[] args) {
        int[] first = {2, 4, 1, 3, 5};
        int[] second = {5, 4, 3, 2, 1};

        System.out.println(countInversions(first));  // 3
        System.out.println(countInversions(second)); // 10
        System.out.println(Arrays.toString(first));   // sorted after counting
    }
}
