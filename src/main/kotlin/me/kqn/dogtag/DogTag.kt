package me.kqn.dogtag

import com.sk89q.worldguard.bukkit.RegionContainer
import com.sk89q.worldguard.bukkit.WorldGuardPlugin
import com.sk89q.worldguard.protection.flags.BooleanFlag
import me.kqn.dogtag.file.ConfigObject.conf
import me.kqn.dogtag.file.MessageObject.message
import org.bukkit.Bukkit
import org.bukkit.Bukkit.getPluginManager
import org.bukkit.Material
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.inventory.ItemStack
import taboolib.common.io.newFile
import taboolib.common.platform.Plugin
import taboolib.common.platform.command.command
import taboolib.common.platform.event.SubscribeEvent
import taboolib.common.platform.function.getDataFolder
import taboolib.common.platform.function.info
import taboolib.common.platform.function.submitAsync
import taboolib.common5.Baffle
import taboolib.common5.FileWatcher
import taboolib.expansion.*
import taboolib.library.xseries.getItemStack
import taboolib.module.chat.colored
import taboolib.module.configuration.Configuration
import taboolib.platform.util.giveItem
import taboolib.platform.util.isRightClick
import taboolib.platform.util.onlinePlayers
import java.io.File
import java.util.concurrent.TimeUnit

object  DogTag : Plugin() {


    lateinit var dogtag:ItemStack
    lateinit var baffle: Baffle
    lateinit var baffle2: Baffle
     var confPath="plugins/DogTag/config.yml"
    override fun onEnable() {
        baffle2=Baffle.of(500.toLong(),TimeUnit.MILLISECONDS)
        baffle=Baffle.of(conf.getInt("CoolDown",10).toLong(),TimeUnit.SECONDS)
        if (conf.getBoolean("database.enable")) {
            setupPlayerDatabase(conf.getConfigurationSection("database")!!)
        } else {
            setupPlayerDatabase(newFile(getDataFolder(), "data.db"))
        }
         dogtag= conf.getItemStack("Item")?: ItemStack(Material.NAME_TAG)
        FileWatcher.INSTANCE.addSimpleListener(File(confPath)){
            reload()
            onlinePlayers.forEach { if (it.isOp){
            it.sendMessage("&a���Զ�����".colored())
            }
            }
        }
        regCmd()
    }

    override fun onDisable() {
        baffle.resetAll()
        baffle2.resetAll()
    }
    private var worldGuardPlugin: WorldGuardPlugin? = null
    private var regionContainer: RegionContainer? = null
    override fun onLoad() {

        this.worldGuardPlugin = getPluginManager().getPlugin("WorldGuard") as WorldGuardPlugin
        val booleanFlag: BooleanFlag = Flag.SAFE_AREA
        this.regionContainer = worldGuardPlugin!!.regionContainer
        try {
            if (worldGuardPlugin!!.flagRegistry.get("DogTag-Safe-Area") != null) return
            worldGuardPlugin!!.flagRegistry.register(booleanFlag)
        } catch (e: Exception) {
            e.printStackTrace()
            Bukkit.getLogger().warning("[DogTag]δ����WorldGuard,��ȫ�����ܽ�������")
        }
    }
    fun  reload(){
        conf= Configuration.loadFromFile(File(confPath))
        dogtag= conf.getItemStack("Item")?: ItemStack(Material.NAME_TAG)
        baffle.resetAll()

        baffle=Baffle.of(conf.getInt("CoolDown",10).toLong(),TimeUnit.SECONDS)
    }
    fun regCmd(){
        command("dogtag"){
            createHelper()
            literal("reload"){
                execute<CommandSender>{
                    sender, context, argument ->
                    submitAsync { reload()
                    sender.sendMessage("&a���")
                    }
                }
            }
            literal("get"){
                dynamic ("����"){
                    execute<Player>{
                        sender, context, argument ->
                        if(argument.toIntOrNull()!=null){
                            sender.giveItem(dogtag.clone().apply { amount=argument.toInt() })
                        }
                        else {
                            sender.sendMessage("&a��������ȷ��������".colored())
                        }

                    }
                }
            }
            literal("points"){
                dynamic ("�����"){
                    execute<CommandSender>{
                        sender, context, argument ->
                        var p=Bukkit.getPlayer(argument)
                        if(p==null){
                            sender.sendMessage("����Ҳ�����")
                        }
                        else {
                            sender.sendMessage("���${argument}����������Ϊ${p.getDataContainer()[pointKey]}")
                            sender.sendMessage("���${argument}�������ȼ�Ϊ${p.getDataContainer()[levelKey]}")
                        }
                    }

                }
            }


        }
    }

    @SubscribeEvent
    fun playerDeath(e:PlayerDeathEvent){
        if(e.entity.killer!=null&&e.entity.killer!=e.entity){
            var killer=e.entity.killer!!
            if (worldGuardPlugin != null) {
                var player=e.entity
                val localPlayer = worldGuardPlugin!!.wrapPlayer(player)
                val regions = regionContainer!!.createQuery().getApplicableRegions(player.getLocation())
                val keepInventory = regions.queryValue(localPlayer, Flag.SAFE_AREA)
                if (keepInventory != null && keepInventory==true) {
                    return
                }
            }
            if(baffle.hasNext(e.entity.name)){
                baffle.next(e.entity.name)
                e.entity.world.dropItemNaturally(e.entity.location, dogtag.also { it.amount=1 })
            }
        }
    }
    val pointKey="dogtag_points"
    val levelKey="dogtag_levels"
    //�Ҽ����ƻ�ȡ����
    @SubscribeEvent
    fun onClick(e:PlayerInteractEvent){
        if(e.isRightClick()){
            var mainhand=e.player.inventory.itemInMainHand

            if(dogtag.apply { amount=mainhand.amount } == mainhand){
                if(!baffle2.hasNext(e.player.name)){
                    return
                }
                baffle2.next(e.player.name)
                mainhand.amount=mainhand.amount-1
                var points= conf.getInt("Points")
                var p= e.player.getDataContainer()[pointKey]
                if(p==null){
                    e.player.getDataContainer().set(pointKey,points)//û�������
                }
                else {
                    e.player.getDataContainer().set(pointKey,p.toInt()+points)//�������
                }
                e.player.sendMessage(message.getString("GET_POINTS","")!!.replace("%amount%",points.toString()).colored())//������Ϣ���滻ռλ��
                //�����ж�����
                var total= e.player.getDataContainer()[pointKey]!!.toInt()
                var levels= conf.getConfigurationSection("Levels")!!.getKeys(false)
                var lvlNow=e.player.getDataContainer()[levelKey]?.toInt()?:0//ȡ���ڵĵȼ�
                for (it in levels) {
                    var need= conf["Levels.${it}.exp"] as Int
                    if(lvlNow<it.toInt()) {
                        if (need <= total) {
                            e.player.sendMessage(
                                message.getString("UPGRADE")!!.replace("%level%", it).colored()
                            )//����������Ϣ
                            e.player.getDataContainer()[levelKey] = it//�ѵȼ��������ݿ�
                            var cmds= conf.getStringList("Levels.${it}.command")
                            for (cmd in cmds) {
                                e.player.isOp=true
                                e.player.performCommand(cmd.replace("[player]",e.player.name))//�����ִ������
                                e.player.isOp=false
                            }
                        }
                        else {
                            break
                        }
                    }
                }


            }
        }
    }
    @SubscribeEvent
    fun setupData(e: PlayerJoinEvent) {
        // ��ʼ���������

        e.player.setupDataContainer()

    }
    @SubscribeEvent
    fun releaseDAta(e: PlayerQuitEvent) {
        // �ͷ������������
        e.player.releaseDataContainer()
    }
}