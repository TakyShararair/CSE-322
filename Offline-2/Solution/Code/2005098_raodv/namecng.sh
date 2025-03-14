# Step 2: Rename all files and directories containing 'raodv', 'raodv', or 'raodv'
echo "Renaming files and directories in raodv..."
find . -depth | while IFS= read -r file; do
    new_name=$(echo "$file" | sed -e 's/raodv/raodv/gI')
    if [ "$file" != "$new_name" ]; then
        mv "$file" "$new_name"
        echo "Renamed: $file → $new_name"
    fi
done

echo "meow"

# Step 3: Replace all occurrences in the content of files
echo "Updating file content in src/raodv..."
find . -type f -exec sed -i 's/\baodv\b/raodv/gI; s/raodv/Raodv/g; s/raodv/RRAODV/g' {} +

# Final Message
echo "Directory copied, files renamed, and content updated successfully! "