package fr.minecraftforgefrance.installer;

public class Version implements Comparable<Version> {
    public final int[] numbers;

    public Version(String version) {
        final String[] split = version.split("\\-")[0].split("\\.");
        numbers = new int[split.length];
        for (int i = 0; i < split.length; i++) {
            numbers[i] = Integer.parseInt(split[i]);
        }
    }

    @Override
    public int compareTo(Version other) {
        final int maxLength = Math.max(numbers.length, other.numbers.length);
        for (int i = 0; i < maxLength; i++) {
            final int left = i < numbers.length ? numbers[i] : 0;
            final int right = i < other.numbers.length ? other.numbers[i] : 0;
            if (left != right) {
                return left < right ? -1 : 1;
            }
        }
        return 0;
    }
}