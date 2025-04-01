package com.adamdoesthings.Novum;

import java.util.ArrayList;
import java.util.Set;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.Repairable;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.plugin.java.JavaPlugin;

import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.TextComponent;

public class Novum extends JavaPlugin {
	/* Notes/To-Do
	 * 
	 * things to sacrifice
	 *  -- 64 black wool
	 *  -- 15 levels
	 *  -- 2 emeralds
	 *  -- ghast tear
	 *  -- blaze powder
	 *  
	 *  player must place enchanting table with chests on either side of it
	 */
	
	int ConfigResetRepairLevel = 2;
	ArrayList<SacrificeOption> ConfiguredSacrifices = new ArrayList<SacrificeOption>();
	
	@Override
	public void onEnable() {
		// need to come look at this again to set different requirements based on command arguments
		this.getCommand("novum").setExecutor(new NovumCommand());

		ArrayList<RequiredSacrifice> RestoreRequirements = new ArrayList<RequiredSacrifice>();
		ArrayList<RequiredSacrifice> TransferRequirements = new ArrayList<RequiredSacrifice>();
		
		// requirements for /novum ritual_restore
		// create item stacks with required items
		ItemStack requiredWool = new ItemStack(Material.BLACK_WOOL);
		ItemStack requiredEmeralds = new ItemStack(Material.EMERALD);
		ItemStack requiredGhastTear = new ItemStack(Material.GHAST_TEAR);
		ItemStack requiredBlazePowder = new ItemStack(Material.BLAZE_POWDER);
		
		// add stacks to requirements
		RestoreRequirements.add(new RequiredSacrifice(requiredWool, 64));
		RestoreRequirements.add(new RequiredSacrifice(requiredEmeralds, 2));
		RestoreRequirements.add(new RequiredSacrifice(requiredGhastTear, 1));
		RestoreRequirements.add(new RequiredSacrifice(requiredBlazePowder, 32));
		
		// requirements for /novum ritual_transfer
		ItemStack requiredLapisDust = new ItemStack(Material.LAPIS_LAZULI);
		ItemStack requiredEmptyBook = new ItemStack(Material.BOOK);
		TransferRequirements.add(new RequiredSacrifice(requiredLapisDust, 2, true));
		TransferRequirements.add(new RequiredSacrifice(requiredEmptyBook, 1));
		// modify quantity later when evaluating item with enchantments to be transferred
		// lapis quantity should be sum of all enchantments' levels * 2 (min 2)
		
		ConfiguredSacrifices.add(new SacrificeOption(RestoreRequirements, "ritual_restore"));
		ConfiguredSacrifices.add(new SacrificeOption(TransferRequirements, "ritual_transfer"));
	}
	
	@Override
	public void onDisable() {
		
	}
	
	public class NovumCommand implements CommandExecutor {
		ConsoleCommandSender Console = Bukkit.getServer().getConsoleSender();
		
		public NovumCommand() {
			
		}
		
		private void GetRepairCost (Player player, boolean isTest) {
			ItemStack itemInMainHand = player.getEquipment().getItemInMainHand();
			// check for enchantments & levels on item
			int totalEnchantLevel = 0;
			java.util.Map<Enchantment, Integer> enchants = itemInMainHand.getEnchantments();
			if (enchants.isEmpty()) {
				SendPlayerErrorMessage("Main hand item contains no enchantments and cannot be used for a transfer ritual.", player);
			}
			else {
				SendPlayerMessage("Main hand item contains the following enchantments:", player);
				for (java.util.Map.Entry<Enchantment, Integer> enchant : enchants.entrySet()) {
					//SendPlayerMessage("TESTONLY Enchant: " + enchant.toString() + " , .getKey(): " + enchant.getKey().toString() + " , .getKey().getKey(): " + enchant.getKey().getKey().toString(), player);
					SendPlayerMessage("Type: " + enchant.getKey().getKey().getKey() + " | Level: " + enchant.getValue().toString(), player);
					totalEnchantLevel += enchant.getValue();
				}
				SendPlayerMessage("Total enchant level is " + Integer.toString(totalEnchantLevel) + " and will cost " + Integer.toString(totalEnchantLevel * 2) + " Lapis Lazuli in a transfer ritual.", player);
				SendPlayerMessage("----", player);
			}
			
			if (itemInMainHand.hasItemMeta()) {
				ItemMeta itemInMainHandMeta = itemInMainHand.getItemMeta();
				if (itemInMainHandMeta instanceof Repairable) {
					Repairable repairMeta = (Repairable) itemInMainHandMeta;
					SendPlayerMessage("Main hand item anvil repair cost: " + Integer.toString(repairMeta.getRepairCost() + 1), player);
				}
				//else if ()
				else {
					SendPlayerErrorMessage("Invalid item. Main hand item must be repairable at an anvil.", player);
				}
			}
			else {
				SendPlayerErrorMessage("Invalid item. Main hand item must be repairable at an anvil.", player);
			}
		}
		
		private void AttemptRitualTransfer (Player player, boolean isTest, ArrayList<RequiredSacrifice> transferReqs) {
			boolean ritualRequirementsFailed = true;
			String ritualRequirementsFailedReason = "The transfer ritual could not be completed for reasons unknown...";
			
			// player must be looking at a chest containing the requisite items
			// check to see if they're looking at a chest, then if the items are there in correct quantities (update lapis qty based on enchant lvl)
			ItemStack itemInMainHand = player.getEquipment().getItemInMainHand();
			int totalEnchantLevel = 0;
			
			java.util.Map<Enchantment, Integer> enchants = itemInMainHand.getEnchantments();
			
			// see if item has any enchantments
			if (enchants.isEmpty()) {
				ritualRequirementsFailedReason = "Main hand item contains no enchantments and cannot be used for a transfer ritual.";
			}
			else {
				// look at enchants and modify required lapis according to total enchant level
				for (java.util.Map.Entry<Enchantment, Integer> enchant : enchants.entrySet()) {
					totalEnchantLevel += enchant.getValue();
				}
				
				for (RequiredSacrifice req : transferReqs) {
					if (req.ModifiedByEnchantLevel) {
						req.Quantity = totalEnchantLevel * 2;
					}
				}
				
				// now check to see if player is looking at chest with required items
				Block playerTargetBlock = player.getTargetBlock((Set<Material>) null, 5);
				if (playerTargetBlock.getType() == Material.CHEST) {
					Chest targetChest = (Chest) playerTargetBlock.getState();
					
					// get chest inventory
					Inventory chestContents = targetChest.getBlockInventory();
					// check for reqs
					boolean missingRequiredItem = false;
					for (RequiredSacrifice req : transferReqs) {
						if (!chestContents.containsAtLeast(req.ItemType, req.Quantity)) {
							ritualRequirementsFailedReason = "'Your sacrifice lacks the requisite " + req.ItemType.getType().toString() + "... offer at least " + Integer.toString(req.Quantity) + " of it...'";
							missingRequiredItem = true;
						}
					}
					
					if (!missingRequiredItem) {
						ritualRequirementsFailed = false;
						// ritual was successful, perform actions & play sound
						
						// empty chest
						chestContents.clear();

						// create new enchanted book item for enchantments to transfer to
						ItemStack newBook = new ItemStack(Material.ENCHANTED_BOOK, 1);
						EnchantmentStorageMeta newBookMeta = (EnchantmentStorageMeta)newBook.getItemMeta();
						
						/*
						 * EnchantmentStorageMeta meta = (EnchantmentStorageMeta)book.getItemMeta();
                		 * meta.addStoredEnchant(Enchantment.ARROW_INFINITE, 1, true);
						 */
						
						// remove enchantments from main hand item, then add to book
						for (java.util.Map.Entry<Enchantment, Integer> enchant : enchants.entrySet()) {
							itemInMainHand.removeEnchantment(enchant.getKey());
							newBookMeta.addStoredEnchant(enchant.getKey(), enchant.getValue(), true);
						}
						
						// apply changes to book and put book in chest
						newBook.setItemMeta(newBookMeta);
						chestContents.addItem(newBook);
						//player.playEffect(EntityEffect.WITCH_MAGIC); broken as of 1.21.4
						player.playSound(player.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1f, 1f);
						SendPlayerColorMessage("The ritual succeeds, and the power of the item transferred to the book in the chest.", ChatColor.DARK_PURPLE, player);
					}
				}
			}
			
			if (ritualRequirementsFailed) {
				SendPlayerErrorMessage("The ritual fails, and a voice only you can hear whispers in the wind...", player);
				SendPlayerErrorMessage(ritualRequirementsFailedReason, player);
				player.playSound(player.getLocation(), Sound.BLOCK_FIRE_EXTINGUISH, 1f, 1f);
			}
		}
		
		private void AttemptRitualRestore (Player player, boolean isTest, ArrayList<RequiredSacrifice> restoreReqs, int resetRepairLevel) {
			ItemStack itemInMainHand = player.getEquipment().getItemInMainHand();
			
			if (itemInMainHand.hasItemMeta()) {
				ItemMeta itemInMainHandMeta = itemInMainHand.getItemMeta();
				if (itemInMainHandMeta instanceof Repairable) {
					Repairable repairMeta = (Repairable) itemInMainHandMeta;
					SendPlayerMessage("Attempting Novum ritual for main hand item (repair cost " + Integer.toString(repairMeta.getRepairCost() + 1) + ")...", player);
					SendPlayerColorMessage("An unseen force gathers around the item...", ChatColor.DARK_GREEN, player);
					
					// check player level
					if (player.getLevel() >= 15) {
						// check sacrificial items, if all present in chest inventory, continue
						Block playerTargetBlock = player.getTargetBlock((Set<Material>) null, 5);
						
						if (playerTargetBlock.getType() == Material.CHEST) {
							// get the chest object
							Chest targetChest = (Chest) playerTargetBlock.getState();
							
							// now get chest inventory
							Inventory chestContents = targetChest.getBlockInventory();
							
							// check contents for required sacrifices from list
							boolean ritualRequirementsFailed = false;
							String failureReason = "";
							
							for (int i = 0; i < restoreReqs.size(); i++) {
								// check for color if wool here when upgraded to 1.13
								
								if (!chestContents.containsAtLeast(restoreReqs.get(i).ItemType, restoreReqs.get(i).Quantity)) {
									ritualRequirementsFailed = true;
									failureReason = "'Your sacrifice lacks the requisite " + restoreReqs.get(i).ItemType.getType().toString() + "... offer at least " + Integer.toString(restoreReqs.get(i).Quantity) + " of it...'";
								}
							}
							
							if (ritualRequirementsFailed) {
								SendPlayerErrorMessage("The ritual fails, and a voice only you can hear whispers in the wind...", player);
								SendPlayerErrorMessage(failureReason, player);
								player.playSound(player.getLocation(), Sound.BLOCK_FIRE_EXTINGUISH, 1f, 1f);
							}
							else {
								repairMeta.setRepairCost(resetRepairLevel);
								itemInMainHand.setItemMeta(itemInMainHandMeta);
								SendPlayerColorMessage("The ritual succeeds, and the power of the item is renewed.", ChatColor.DARK_PURPLE, player);
								chestContents.clear();
								player.setLevel(player.getLevel() - 15);
								//player.playEffect(EntityEffect.WITCH_MAGIC); maybe never worked - broken as of 1.21.4
								player.playSound(player.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1f, 1f);
							}
						}
						else {
							SendPlayerErrorMessage("The ritual fails, and a voice only you can hear whispers in the wind...", player);
							SendPlayerErrorMessage("'Look upon a container which holds the requisite sacrifice...'", player);
							player.playSound(player.getLocation(), Sound.BLOCK_FIRE_EXTINGUISH, 1f, 1f);
						}
					}
					else {
						SendPlayerErrorMessage("The ritual fails, and a voice only you can hear whispers in the wind...", player);
						SendPlayerErrorMessage("'You are unworthy... seek more experience...'", player);
						player.playSound(player.getLocation(), Sound.BLOCK_FIRE_EXTINGUISH, 1f, 1f);
					}
				}
				else {
					SendPlayerErrorMessage("The ritual fails, and a voice only you can hear whispers in the wind...", player);
					SendPlayerErrorMessage("'This item is unacceptable for renewal... hold in your main hand an item repairable at an anvil...'", player);
					player.playSound(player.getLocation(), Sound.BLOCK_FIRE_EXTINGUISH, 1f, 1f);
				}
			}
			else {
				SendPlayerErrorMessage("The ritual fails, and a voice only you can hear whispers in the wind...", player);
				SendPlayerErrorMessage("'This item is unacceptable for renewal... hold in your main hand an item repairable at an anvil...'", player);
				player.playSound(player.getLocation(), Sound.BLOCK_FIRE_EXTINGUISH, 1f, 1f);
			}
		}
		
		private void SendPlayerErrorMessage(String msg, Player target) {
			TextComponent message = new TextComponent(msg);
			message.setColor(ChatColor.RED);
			target.spigot().sendMessage(message);
		}
		
		private void SendPlayerMessage(String msg, Player target) {
			TextComponent message = new TextComponent(msg);
			message.setColor(ChatColor.AQUA);
			target.spigot().sendMessage(message);
		}
		
		private void SendPlayerColorMessage(String msg, ChatColor color, Player target) {
			TextComponent message = new TextComponent(msg);
			message.setColor(color);
			target.spigot().sendMessage(message);
		}
		
		@Override
		public boolean onCommand(CommandSender sender, Command cmd, String cmdLabel, String[] cmdArgs) {
			if (sender instanceof Player)
			{
				try
				{
					Player player = (Player)sender;
					
					if (cmdArgs.length > 1)
					{
						SendPlayerErrorMessage("Unknown Novum command. Try '/novum help' for assistance.", player);
						Console.sendMessage(player.getDisplayName() + " entered invalid Novum command arguments.");
					}
					else
					{
						if (cmdArgs.length == 0)
						{
							SendPlayerMessage("Novum provides several commands that revolve around a ritual item & XP sacrifice that", player);
							SendPlayerMessage("allows players to reset the anvil repair cost of enchanted items.", player);
							SendPlayerMessage("Type '/novum help' or '/novum ?' for more details.", player);
						}
						else
						{
							String convertedArg = cmdArgs[0].toUpperCase();
							ArrayList<RequiredSacrifice> reqs = new ArrayList<RequiredSacrifice>();
							int level = 1;
							boolean foundMatchingSacrificeConfig = false;
							
							switch (convertedArg)
							{
								case "HELP": case "?":
									SendPlayerMessage("/novum getCost: Shows the current anvil repair cost of main hand item.", player);
									SendPlayerMessage("/novum ritual_restore: Attempt the ritual to reset the anvil repair cost of main hand item.", player);
									SendPlayerMessage("/novum ritual_transfer: Attempt the ritual to transfer the enchantments of the main hand item to a book in targeted chest.", player);
									break;
								case "GETCOST":
									GetRepairCost(player, false);
									break;
								case "RITUAL_RESTORE":
									// run through each configured sacrifice option looking for one matching the upper case argument text
									for (SacrificeOption opt: ConfiguredSacrifices) {
										
										if (opt.Argument.toUpperCase().equals(convertedArg)) {
											// if a match is found, update the values to pass to the ritual function
											reqs = opt.SacrificeList;
											level = ConfigResetRepairLevel;
											foundMatchingSacrificeConfig = true;
										}
									}
									
									if (foundMatchingSacrificeConfig) {
										AttemptRitualRestore(player, false, reqs, level);
									}
									else {
										SendPlayerMessage("Couldn't find matching sacrifice configuration for Novum modifier '" + cmdArgs[0] + "' - check plugin configuration.", player);
									}
									
									break;
								case "RITUAL_TRANSFER":									
									// run through each configured sacrifice option looking for one matching the upper case argument text
									for (SacrificeOption opt: ConfiguredSacrifices) {
										
										if (opt.Argument.toUpperCase().equals(convertedArg)) {
											// if a match is found, update the values to pass to the ritual function
											reqs = opt.SacrificeList;
											foundMatchingSacrificeConfig = true;
										}
									}
									
									if (foundMatchingSacrificeConfig) {
										AttemptRitualTransfer(player, false, reqs);
									}
									else {
										SendPlayerMessage("Couldn't find matching sacrifice configuration for Novum modifier '" + cmdArgs[0] + "' - check plugin configuration.", player);
									}
									
									break;
								default:
									SendPlayerMessage("Invalid Novum modifier '" + cmdArgs[0] + "' - use '/novum help' for assistance.", player);
							}
						}
					}

					// if command worked, return true
					return true;
				}
				catch (Exception ex)
				{
					SendPlayerErrorMessage("Error running Novum command.", (Player)sender);
					Console.sendMessage(ex.getMessage());
					Console.sendMessage(ex.getStackTrace().toString());
				}
			}
			else
			{
				Console.sendMessage("Invalid sender for Novum command. Sender must be player.");
			}
			return false;
		}
	}
	
	public class RequiredSacrifice {
		
		public ItemStack ItemType;
		public int Quantity;
		public boolean ModifiedByEnchantLevel;
		
		public RequiredSacrifice(ItemStack item, int qty) {
			ItemType = item;
			Quantity = qty;
			ModifiedByEnchantLevel = false;
		}
		public RequiredSacrifice(ItemStack item, int qty, boolean enchantLevelModsQuantity) {
			ItemType = item;
			Quantity = qty;
			ModifiedByEnchantLevel = enchantLevelModsQuantity;
		}
	}
	
	public class SacrificeOption {
		public ArrayList<RequiredSacrifice> SacrificeList;
		public String Argument;
		
		public SacrificeOption(ArrayList<RequiredSacrifice> sacrifices, String arg_text) {
			SacrificeList = sacrifices;
			Argument = arg_text;
		}
	}
}
