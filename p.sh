#!/bin/bash

timestamp=$(date +%s)
echo "::: > $timestamp"
git add .
git commit -m "||| $timestamp"
git push origin main