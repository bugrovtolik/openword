const fs = require('fs');
const path = require('path');

const targetDir = process.argv[2];
if (!targetDir) {
    console.error('Usage: node generate-sw.js <target-directory>');
    process.exit(1);
}

const distDir = path.resolve(__dirname, targetDir);
const templatePath = path.join(__dirname, 'src/jsMain/resources/sw.template.js');
const outputPath = path.join(distDir, 'sw.js');

// Recursively get all files, return as URL paths
function getAllFiles(dir, baseDir = dir) {
    let results = [];
    if (!fs.existsSync(dir)) return results;
    const list = fs.readdirSync(dir);
    list.forEach(file => {
        const fullPath = path.join(dir, file);
        const stat = fs.statSync(fullPath);
        if (stat && stat.isDirectory()) {
            results = results.concat(getAllFiles(fullPath, baseDir));
        } else {
            const relative = path.relative(baseDir, fullPath).replace(/\\/g, '/');
            results.push(relative);
        }
    });
    return results;
}

const files = getAllFiles(distDir);
if (!files.includes('')) files.unshift('');
if (!files.includes('index.html')) files.unshift('index.html');

const newContent = fs.readFileSync(templatePath, 'utf8')
    .replace('__ASSETS_PLACEHOLDER__', JSON.stringify(files, null, 2))
    .replace('__CACHE_NAME__', Date.now());

fs.writeFileSync(outputPath, newContent);