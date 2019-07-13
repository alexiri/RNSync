const path = require('path');
const fs = require('fs-extra');

const RNSYNC_DIR = path.join('..');
const RNSYNC_EXAMPLE_DIR = path.join('node_modules', 'rnsync');

function copyFile(source, dest) {
  return new Promise((resolve, reject) => {
    fs.copy(source, dest, (err) => {
      if (err) {
        return reject(err);
      }
      return resolve();
    });
  });
}

async function main () {
  try {
    console.log('Copying javascript');
    await copyFile(path.join(RNSYNC_EXAMPLE_DIR, 'index.js'), path.join(RNSYNC_DIR, 'index.js'));

    console.log('Copying java');
    await copyFile(path.join(RNSYNC_EXAMPLE_DIR, 'android', 'src'), path.join(RNSYNC_DIR, 'android', 'src'));

    console.log('Copying gradle file');
    await copyFile(path.join(RNSYNC_EXAMPLE_DIR, 'android', 'build.gradle'), path.join(RNSYNC_DIR, 'android', 'build.gradle'));

    console.log('Copying objc');
    await copyFile(path.join(RNSYNC_EXAMPLE_DIR, 'ios', 'RNSync'), path.join(RNSYNC_DIR, 'ios', 'RNSync'));

    console.log('Copying xcode project');
    await copyFile(path.join(RNSYNC_EXAMPLE_DIR, 'ios', 'RNSync', 'RNSync.xcodeproj'), path.join(RNSYNC_DIR, 'ios', 'RNSync', 'RNSync.xcodeproj'));
  } catch (e) {
    console.log(e);
  }
}

main();
