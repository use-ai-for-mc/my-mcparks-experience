#!/usr/bin/env python3
"""Verify that ride registry entries match the MCParks resource pack iron_axe.json."""

import json
import subprocess
import sys

def extract_resource_pack_data():
    """Extract vehicle model mappings from the MCParks resource pack."""
    cmd = [
        "unzip", "-p",
        "/Users/cusgadmin/Library/Application Support/ModrinthApp/profiles/Fabric 1.19/resourcepacks/mcparkspack1-19.zip",
        "assets/minecraft/models/item/iron_axe.json"
    ]
    result = subprocess.run(cmd, capture_output=True, text=True)
    if result.returncode != 0:
        print("ERROR: Could not extract iron_axe.json from resource pack")
        sys.exit(1)
    
    data = json.loads(result.stdout)
    max_dur = 250  # iron_axe max durability
    
    # Build damage -> model mapping
    pack_models = {}
    for override in data.get("overrides", []):
        pred = override.get("predicate", {})
        damage_frac = pred.get("damage", 0)
        model = override.get("model", "")
        
        damage = round(damage_frac * max_dur)
        if model:
            pack_models[damage] = model
    
    return pack_models

def load_rides_registry():
    """Load our rides registry JSON."""
    path = "/Users/cusgadmin/Library/Application Support/ModrinthApp/profiles/Fabric 1.19/config/mcparks-rides.json"
    with open(path) as f:
        return json.load(f)

def verify():
    print("=" * 70)
    print("Verifying ride registry against MCParks resource pack")
    print("=" * 70)
    print()
    
    pack_models = extract_resource_pack_data()
    print(f"Resource pack has {len(pack_models)} model overrides for iron_axe")
    
    rides_data = load_rides_registry()
    rides = rides_data.get("rides", [])
    print(f"Rides registry has {len(rides)} rides")
    print()
    
    # Check each vehicle in our registry
    errors = []
    warnings = []
    verified = 0
    
    for ride in rides:
        ride_name = ride.get("name", "Unknown")
        for vehicle in ride.get("vehicles", []):
            item = vehicle.get("item", "")
            damage = vehicle.get("damage", 0)
            our_model = vehicle.get("modelPath", "")
            
            if item != "iron_axe":
                warnings.append(f"Non-iron_axe item: {item}:{damage} in {ride_name}")
                continue
            
            if damage not in pack_models:
                errors.append(f"MISSING: damage={damage} not in resource pack (ride: {ride_name}, model: {our_model})")
                continue
            
            pack_model = pack_models[damage]
            if our_model and our_model != pack_model:
                errors.append(f"MISMATCH: damage={damage} - ours: {our_model}, pack: {pack_model} (ride: {ride_name})")
            else:
                verified += 1
    
    # Report results
    print(f"Verified: {verified} vehicles match resource pack")
    print()
    
    if warnings:
        print(f"WARNINGS ({len(warnings)}):")
        for w in warnings:
            print(f"  - {w}")
        print()
    
    if errors:
        print(f"ERRORS ({len(errors)}):")
        for e in errors:
            print(f"  - {e}")
        print()
        return False
    else:
        print("All entries verified successfully!")
        return True

if __name__ == "__main__":
    success = verify()
    sys.exit(0 if success else 1)
